# Wave lite

### Summary

Wave can be deployed in "lite" mode that requires minimal configuration and maintenance. When
running in "Lite" mode, Wave only support container augmentation capability. This allows, for example,
the use of Fusion file system in Nextflow pipeline, however the following feature are not available:
- container build on-demand
- container freeze
- container security scans
- container mirror

### Requirement

* EC2 instance type (`m5a.2xlarge` instance type)
* Redis 6.2.x or later (`cache.t3.medium` instance type)
* Postgres 16 or later


### Deployment 

1. Initialize the Docker swarm environment 
  
    docker swarm init

2. Deploy the Wave service (running 2 replicas)
  
    docker stack deploy -c docker-compose.yml mystack

3. Check the current status
  
    docker service ls

4. Check the logs 

    docker service logs mystack_wave


5. Tear down when donw

    docker stack rm mystack