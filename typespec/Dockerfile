FROM nginx:alpine

# Copy index.html and openapi.yaml to the Nginx html directory
COPY index.html /usr/share/nginx/html/openapi/index.html
COPY tsp-output/@typespec/openapi3/openapi.yaml /usr/share/nginx/html/openapi/openapi.yaml

# Expose port 9080
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
