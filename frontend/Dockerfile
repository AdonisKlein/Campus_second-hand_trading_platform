FROM nginx:1.28-alpine
ARG APP_VERSION=dev
ARG GIT_COMMIT=local
LABEL org.opencontainers.image.version=$APP_VERSION org.opencontainers.image.revision=$GIT_COMMIT
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY *.html /usr/share/nginx/html/
COPY assets /usr/share/nginx/html/assets
EXPOSE 80
