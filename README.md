# tower-reg 

Prototype for an epherable container registry that injects 
a custom payloads durin an arbitrary image pull. 

### How it works 

[TLDR;](tldr.md)

### Get started 

1. Clone this repo and change into the project root

2. Compile and run the registry service  

    bash run.sh

3. Use reverse proxy service to expose the registry with public name, e.g. 

    ngrok http 9090 -subdomain reg


4. Pull a container using the docker client 


    docker pull reg.ngrok.io/library/busybox

5. The pulled images, contains an extra file `/foo/foo.txt` 


    docker run reg.ngrok.io/library/busybox cat foo/foo.txt
