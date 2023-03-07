# Frequently Asked questions

### Can I use private container repositories

Yes. The container registry credentials should be provided via Tower credentials
manager, in the workspace from where the pipeline execution is launched.

### Can I use private container repositories if a launch the pipeline with Nextflow, not via Tower

Yes. You will need to create a Tower account and provide the container credentials in your own
personal workspace or an organization workspace.

Then include the Tower access token and the workspace ID (only if using organisation workspaces) in the Nextflow
configuration file. [LINK TO DOC]

### Does Wave modify my containers

No. Wave does alter or modify your container images. Wave acts as proxy server
in between the Docker client (or equivalent container engine) and the target registry
from where the container image is hosted.

The container image when pulled from Wave can however include some extra content,
as required by the pipeline execution, by using what's called container aurgmentation.
