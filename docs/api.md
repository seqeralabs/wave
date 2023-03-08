---
title: API documentation
description: Endpoints for the Wave API
hide:
    - navigation
    - toc
---

# API documentation

To be done

!!! api-get "/actions"

    ### Request

    #### Query-string parameters

    `workspaceId`

    :   Workspace numeric identifier

    `attributes`

    :   Comma-separated list of attributes to retrieve: labels. Empty to retrieve nothing

    ### Response

    ```json
    {
        actions: [{
            id: string
            name: string
            pipeline: string
        }]
    }
    ```

!!! api-put "/actions"

    ### Request

    #### Query-string parameters

    `workspaceId`

    :   Workspace numeric identifier

    `attributes`

    :   Comma-separated list of attributes to retrieve: labels. Empty to retrieve nothing

    ### Response

    ```json
    {
        actions: [{
            id: string
            name: string
            pipeline: string
        }]
    }
    ```

!!! api-post "/actions"

    ### Request

    #### Query-string parameters

    `workspaceId`

    :   Workspace numeric identifier

    `attributes`

    :   Comma-separated list of attributes to retrieve: labels. Empty to retrieve nothing

    ### Response

    ```json
    {
        actions: [{
            id: string
            name: string
            pipeline: string
        }]
    }
    ```

!!! api-delete "/actions"

    ### Request

    #### Query-string parameters

    `workspaceId`

    :   Workspace numeric identifier

    `attributes`

    :   Comma-separated list of attributes to retrieve: labels. Empty to retrieve nothing

    ### Response

    ```json
    {
        actions: [{
            id: string
            name: string
            pipeline: string
        }]
    }
    ```
