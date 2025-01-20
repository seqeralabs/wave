FROM nginx:alpine

# Copy index.html and openapi.yaml to the Nginx html directory
COPY index.html /usr/share/nginx/html/openapi/index.html
COPY tsp-output/@typespec/openapi3/openapi.yaml /usr/share/nginx/html/openapi/openapi.yaml

# Use sed to modify the default Nginx configuration to listen on port 9080
RUN sed -i 's/listen       80;/listen       9080;/' /etc/nginx/conf.d/default.conf

# Expose port 9080
EXPOSE 9080

CMD ["nginx", "-g", "daemon off;"]
