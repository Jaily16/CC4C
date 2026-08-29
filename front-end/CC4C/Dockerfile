# syntax=docker/dockerfile:1.7
FROM node:26.8.1-alpine3.23@sha256:871eb674ad6e692c91330a8959f1ce2f80ba3f445cdc54e306869d2ea265e42d AS build
WORKDIR /workspace
RUN test "$(node --version)" = "v24.18.0" \
    && test "$(npm --version)" = "11.16.0"
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
ARG VITE_API_BASE_URL=http://localhost:4080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.28.3-alpine3.23@sha256:a8b39bd9cf0f83869a2162827a0caf6137ddf759d50a171451b335cecc87d236
RUN apk del --no-cache \
        nginx-module-acme \
        nginx-module-geoip \
        nginx-module-image-filter \
        nginx-module-njs \
        nginx-module-xslt \
        nginx \
    && apk add --no-cache nginx=1.28.3-r7 \
    && apk upgrade --no-cache
COPY docker/nginx.conf /etc/nginx/nginx.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
RUN mkdir -p /var/cache/nginx /var/run/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run/nginx /usr/share/nginx/html
USER nginx
EXPOSE 8080
