function copyToClipboard(textToCopy, copyBtn) {
    navigator.clipboard.writeText(textToCopy).then(() => {
        copyBtn.innerHTML = `
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24">
                    <path fill="currentColor" fill-rule="evenodd" d="M8.657 18.435L3 12.778l1.414-1.414l4.95 4.95L20.678 5l1.414 1.414l-12.02 12.021a1 1 0 0 1-1.415 0"/>
                </svg>
                <span style="margin-left: 5px; ">Copied!</span>
            `;

        setTimeout(() => {
            copyBtn.innerHTML = `
                    <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24">
                          <path fill="currentColor" d="M19 21H8V7h11m0-2H8a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2m-3-4H4a2 2 0 0 0-2 2v14h2V3h12z"/>
                     </svg>
                `;
        }, 2000);
    }).catch(err => {
        console.error('Failed to copy text: ', err);
    });
}
