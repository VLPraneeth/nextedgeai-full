FROM node:20.18.3-bookworm-slim

LABEL maintainer="Francis Callo <francis@syncari.com>"

WORKDIR /spectrum

ADD proxy /spectrum

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=5 CMD curl -X GET -k -f http://localhost/version || exit 1

ENTRYPOINT ["node", "dist/index.js"]
