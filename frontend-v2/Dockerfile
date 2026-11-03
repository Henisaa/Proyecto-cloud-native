FROM node:22-alpine AS test
WORKDIR /app
COPY index.html config.js ./
COPY js ./js
COPY tests ./tests
RUN node --test tests/*.test.cjs

FROM nginxinc/nginx-unprivileged:alpine AS runtime
USER root
RUN apk add --no-cache jq
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=test /app/index.html /app/config.js /usr/share/nginx/html/
COPY --from=test /app/js /usr/share/nginx/html/js
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh \
 && chown -R 101:101 /usr/share/nginx/html
USER 101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1
