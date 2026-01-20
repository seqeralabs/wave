function copyToClipboard(textToCopy, copyBtn) {
    navigator.clipboard.writeText(textToCopy).then(() => {
        copyBtn.innerHTML = `
                <svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24">
                    <path fill="currentColor" fill-rule="evenodd" d="M8.657 18.435L3 12.778l1.414-1.414l4.95 4.95L20.678 5l1.414 1.414l-12.02 12.021a1 1 0 0 1-1.415 0"/>
                </svg>
                <span style="margin-left: 5px;">Copied!</span>
            `;

        setTimeout(() => {
            copyBtn.innerHTML = `
                    <svg xmlns="http://www.w3.org/2000/svg" width="1em" height="1em" viewBox="0 0 24 24">
                        <path fill="currentColor" d="M19 21H8V7h11m0-2H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2m-3-4H4a2 2 0 0 0-2 2v14h2V3h12z"/>
                    </svg>
                `;
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy text: ', err);
    });
}

// Initialize event listeners when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Handle copy buttons with data-copy attribute
    document.querySelectorAll('[data-copy]').forEach(button => {
        button.addEventListener('click', function() {
            const textToCopy = this.getAttribute('data-copy');
            copyToClipboard(textToCopy, this);
        });
    });

    // Handle copy buttons with data-copy-from-element attribute
    document.querySelectorAll('[data-copy-from-element]').forEach(button => {
        button.addEventListener('click', function() {
            const elementId = this.getAttribute('data-copy-from-element');
            const element = document.getElementById(elementId);
            if (element) {
                copyToClipboard(element.textContent, this);
            }
        });
    });

    // Handle download buttons with data-download-url attribute
    document.querySelectorAll('[data-download-url]').forEach(button => {
        button.addEventListener('click', function() {
            const url = this.getAttribute('data-download-url');
            if (url) {
                window.open(url, '_blank');
            }
        });
    });

    // Handle evict from cache button
    const evictButton = document.getElementById('evictFromCacheBtn');
    if (evictButton) {
        evictButton.addEventListener('click', evictFromCache);
    }
});

function evictFromCache() {
    const statusLabel = document.getElementById("statusLabel");
    const evictTable = document.getElementById("evictTable");
    const evictStatusTable = document.getElementById("evictStatusTable");
    const evictButton = document.getElementById('evictFromCacheBtn');
    const token = evictButton.getAttribute('data-token');
    const serverUrl = evictButton.getAttribute('data-server-url');

    fetch(`${serverUrl}/container-token/${token}`, {
        method: 'DELETE'
    }).then(response => {
        if (response.ok) {
            statusLabel.textContent = "This wave container record has been evicted from cache.";
        } else if (response.status === 404) {
            statusLabel.textContent = "This wave container record is already evicted from cache.";
        } else {
            statusLabel.textContent = "Error evicting the wave container record. Please try again later.";
        }
    }).catch(() => {
        statusLabel.textContent = "Error evicting the wave container record. Please try again later.";
    });
    evictTable.style.display = "none";
    evictStatusTable.style.display = "block";
}
