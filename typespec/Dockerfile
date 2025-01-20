FROM nginx:alpine

# Copy index.html and openapi.yaml to the Nginx html directory
COPY index.html /usr/share/nginx/html/openapi/index.html
COPY tsp-output/@typespec/openapi3/openapi.yaml /usr/share/nginx/html/openapi/openapi.yaml

# Copy the custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 9080
EXPOSE 9080

CMD ["nginx", "-g", "daemon off;"]
