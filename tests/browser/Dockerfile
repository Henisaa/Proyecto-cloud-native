FROM docker:27-cli AS docker-cli
FROM mcr.microsoft.com/playwright:v1.56.1-noble
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /app
COPY tests/browser/package.json ./
RUN npm install --ignore-scripts --no-audit --no-fund
COPY tests/browser/ ./
ENV CI=true
CMD ["npx", "playwright", "test"]
