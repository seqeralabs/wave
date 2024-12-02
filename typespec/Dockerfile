# Use an official Nginx image from the Docker Hub
FROM nginx:alpine

# Copy the index.html and openapi.yaml to the Nginx html directory
COPY index.html /usr/share/nginx/html/
COPY tsp-output/@typespec/openapi3/openapi.yaml /usr/share/nginx/html/

# Expose port 80
EXPOSE 80

# Start Nginx when the container starts
CMD ["nginx", "-g", "daemon off;"]
