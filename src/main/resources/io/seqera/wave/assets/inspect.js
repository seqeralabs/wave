function createTreeView(data) {
    const ul = document.createElement('ul');
    ul.classList.add('tree');

    for (const key in data) {
        if (data.hasOwnProperty(key)) {
            const li = document.createElement('li');

            if (typeof data[key] === 'object' && !Array.isArray(data[key]) && data[key] !== null && Object.keys(data[key]).length > 0) {
                const details = document.createElement('details');
                const summary = document.createElement('summary');
                summary.textContent = key;
                details.appendChild(summary);
                details.appendChild(createTreeView(data[key]));
                li.appendChild(details);

                // Auto-expand first level
                setTimeout(() => summary.click(), 10);
            } else if (Array.isArray(data[key]) && data[key].length > 0) {
                const details = document.createElement('details');
                const summary = document.createElement('summary');
                summary.textContent = key;
                details.appendChild(summary);

                const nestedUl = document.createElement('ul');
                nestedUl.classList.add('tree');
                data[key].forEach((item, index) => {
                    const nestedLi = document.createElement('li');

                    if (typeof item === 'object' && item !== null) {
                        const itemDetails = document.createElement('details');
                        const itemSummary = document.createElement('summary');
                        itemSummary.textContent = `Item ${index + 1}`;
                        itemDetails.appendChild(itemSummary);
                        itemDetails.appendChild(createTreeView(item));
                        nestedLi.appendChild(itemDetails);
                        setTimeout(() => itemSummary.click(), 10);
                    } else {
                        const span = document.createElement('span');
                        span.className = 'tree-text';
                        span.textContent = item;
                        nestedLi.appendChild(span);
                    }

                    nestedUl.appendChild(nestedLi);
                });

                details.appendChild(nestedUl);
                li.appendChild(details);
                setTimeout(() => summary.click(), 10);
            } else {
                const span = document.createElement('span');
                span.className = 'tree-text';
                span.textContent = `${key}: ${JSON.stringify(data[key])}`;
                li.appendChild(span);
            }

            ul.appendChild(li);
        }
    }

    return ul;
}

function createNestedTree(data, serverUrl, imageName) {
    if (typeof data !== "object" || data === null) {
        const span = document.createElement('span');
        span.className = 'tree-text';
        span.textContent = data;
        return span;
    }

    const ul = document.createElement("ul");

    Object.entries(data).forEach(([key, value]) => {
        const li = document.createElement("li");

        if (key === "digest") {
            const span = document.createElement('span');
            span.className = 'tree-text';
            span.textContent = `${key}: `;

            const link = document.createElement("a");
            const name = imageName.split(':')[0];
            link.href = `${serverUrl}/view/inspect?image=${name}@${value}&platform=${data.platform?.architecture || ''}`;
            link.textContent = `${value}`;

            li.appendChild(span);
            li.appendChild(link);
        } else if (typeof value === "object" && value !== null) {
            const details = document.createElement("details");
            const summary = document.createElement("summary");
            summary.textContent = key;

            details.appendChild(summary);
            details.appendChild(createNestedTree(value, serverUrl, imageName));

            li.appendChild(details);
            setTimeout(() => summary.click(), 10);
        } else {
            const span = document.createElement('span');
            span.className = 'tree-text';
            span.textContent = `${key}: ${value}`;
            li.appendChild(span);
        }

        ul.appendChild(li);
    });

    return ul;
}

function createManifestTreeView(data, divId, serverUrl, imageName) {
    const div = document.getElementById(divId);
    const ul = document.createElement("ul");
    ul.className = "tree";

    data.forEach((item, index) => {
        const li = document.createElement("li");
        const details = document.createElement("details");
        const summary = document.createElement("summary");
        summary.textContent = `Manifest ${index + 1}`;
        details.appendChild(summary);

        const subTree = createNestedTree(item, serverUrl, imageName);
        if (subTree) {
            details.appendChild(subTree);
        }

        li.appendChild(details);
        ul.appendChild(li);
        setTimeout(() => summary.click(), 10);
    });

    div.appendChild(ul);
}

// Read data from the page and render the appropriate view
document.addEventListener('DOMContentLoaded', function() {
    const dataEl = document.getElementById('inspect-data');
    if (!dataEl) return;

    var type = dataEl.getAttribute('data-type');

    if (type === 'index') {
        var manifestsEl = document.getElementById('inspect-manifests');
        var manifests = JSON.parse(manifestsEl.value);
        var serverUrl = dataEl.getAttribute('data-server-url');
        var imageName = dataEl.getAttribute('data-image-name');
        createManifestTreeView(manifests, "manifests-div", serverUrl, imageName);
    } else {
        var configEl = document.getElementById('inspect-config');
        var manifestEl = document.getElementById('inspect-manifest');
        if (configEl && configEl.value) {
            document.getElementById('config-div').appendChild(createTreeView(JSON.parse(configEl.value)));
        }
        if (manifestEl && manifestEl.value) {
            document.getElementById('manifest-div').appendChild(createTreeView(JSON.parse(manifestEl.value)));
        }
    }
});
