FROM nginxinc/nginx-unprivileged:alpine

# Copy index.html and openapi.yaml to the Nginx html directory
COPY index.html /usr/share/nginx/html/openapi/index.html
COPY tsp-output/@typespec/openapi3/openapi.yaml /usr/share/nginx/html/openapi/openapi.yaml

# Expose port 8080
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
