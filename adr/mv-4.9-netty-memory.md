# Micronaut 4.9.2 Netty Memory Issue Analysis

## Issue Summary

After upgrading to Micronaut 4.9.2, Wave started experiencing OutOfMemoryError related to direct buffer memory allocation:

```
java.lang.OutOfMemoryError: Cannot reserve 262144 bytes of direct buffer memory (allocated: 104644895, limit: 104857600)
```

## Root Cause Analysis

### The Chain of Changes

1. **Micronaut 4.9.2** upgraded to **Netty 4.2.0** 
2. **Netty 4.2.0** changed the default buffer allocator from `pooled` to `adaptive`
3. The **adaptive allocator** has different direct memory allocation behavior than the pooled allocator

### Netty 4.2 Buffer Allocator Changes

From the Netty 4.2.0 release notes:
> "The adaptive memory allocator is now the default, replacing the pooled allocator. We believe that most workloads will observe reduced memory usage, with performance that is on par or slightly better than the pooled allocator. The adaptive allocator automatically tunes itself to perform well for the observed workload."

### Monitoring Evidence

The direct memory usage graph clearly shows the problem pattern:

- **Stable baseline**: ~40MB usage for hours (normal operation)
- **Sudden spikes**: Two instances simultaneously hit the 100MB limit at 09:00
- **Immediate failure**: Memory usage drops back to ~20MB after hitting the limit
- **Pattern**: Not a gradual memory leak, but allocation spikes during peak activity

This signature is typical of the adaptive allocator attempting to pre-allocate larger buffers based on observed workload patterns, then hitting the JVM's direct memory limit.

## Technical Details

### Current JVM Configuration

```bash
-XX:+UseG1GC 
-Xms512m 
-Xmx850m 
-XX:MaxDirectMemorySize=100m 
-Dio.netty.maxDirectMemory=0
```

### Adaptive vs Pooled Allocator Behavior

**Pooled Allocator (Netty 4.1 default)**:
- Uses fixed memory pools with predictable allocation patterns  
- Memory divided into heap and direct arenas (16MB chunks by default)
- ThreadLocal cache for recently released buffers
- Consistent ~40MB direct memory usage for Wave workload

**Adaptive Allocator (Netty 4.2 default)**:
- Automatically tunes allocation based on observed workload
- More aggressive pre-allocation during peak periods
- Designed for virtual thread compatibility
- Can spike memory usage when learning workload patterns

## Solution

### Implemented Fix

Added `-Dio.netty.allocator.type=pooled` to revert to the stable Netty 4.1 allocator behavior.

**Files Modified:**
- `lite/docker-compose.yml`
- `src/main/jib/launch.sh`

### Alternative Solutions Considered

1. **Increase Direct Memory Limit**
   - Change `-XX:MaxDirectMemorySize=100m` to `200m` or `256m`
   - Keep adaptive allocator but provide more headroom
   - Risk: May just delay the problem

2. **Force Heap Allocation** 
   - Add `-Dio.netty.noPreferDirect=true`
   - Forces heap buffers instead of direct buffers
   - Risk: Performance impact from GC pressure

3. **Remove Conflicting Settings**
   - Remove `-Dio.netty.maxDirectMemory=0` which forces heap allocation
   - May conflict with adaptive allocator's direct memory strategy

## Performance Implications

### Short-term (Current Fix)
- Restores stable memory usage pattern
- Maintains existing performance characteristics
- No risk of regression since this was the previous working configuration

### Long-term Considerations
- The adaptive allocator may offer better performance for some workloads
- Future testing could evaluate adaptive allocator with increased direct memory limits
- Monitor memory usage patterns if/when re-evaluating the allocator choice

## Monitoring Recommendations

Wave already has Netty metrics enabled via Prometheus:
```yaml
netty:
  bytebuf-allocators:
    enabled: true
```

Key metrics to monitor:
- Direct buffer memory usage trends
- Buffer allocation/deallocation rates  
- Memory usage patterns during peak hours
- Any correlation between traffic patterns and memory spikes

## Future Upgrade Strategy

When considering future Micronaut/Netty upgrades:

1. **Test allocator behavior** in staging environment first
2. **Monitor memory patterns** during typical workload cycles
3. **Consider gradual rollout** with adaptive allocator + increased limits
4. **Benchmark performance** differences between allocator types

## References

- [Micronaut 4.9.0 Release Notes](https://micronaut.io/2025/06/30/micronaut-framework-4-9-0-released/)
- [Netty 4.2.0 Release Notes](https://netty.io/news/2025/04/03/4-2-0.html)  
- [Netty 4.2 Migration Guide](https://netty.io/wiki/netty-4.2-migration-guide.html)

---
*Document created: 2025-08-07*  
*Wave version: 1.23.1*  
*Micronaut version: 4.9.2*  
*Netty version: 4.2.0*