FROM node:22-alpine AS build
WORKDIR /app
RUN apk add --no-cache chromium
ENV CHROME_BIN=/usr/bin/chromium-browser
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm test -- --watch=false --browsers=ChromeHeadlessDocker --code-coverage
RUN npm run build

FROM scratch AS reports
COPY --from=build /app/coverage /coverage

FROM nginxinc/nginx-unprivileged:alpine AS runtime
USER root
RUN apk add --no-cache jq
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /app/dist/frontend-rutaexpress/browser /usr/share/nginx/html
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh && chown -R 101:101 /usr/share/nginx/html
USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
