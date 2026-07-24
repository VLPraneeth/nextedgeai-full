FROM node:20.18.3-bookworm-slim

LABEL maintainer="Francis Callo <francis@syncari.com>"

WORKDIR /spectrum-component

ADD storybook-static /spectrum-component

EXPOSE 80

ENTRYPOINT ["/spectrum-component/node_modules/http-server/bin/http-server", "/spectrum-component/", "--port=80"]
