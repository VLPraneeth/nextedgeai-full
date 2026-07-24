FROM golang as builder

WORKDIR /go/src/app
COPY . .

RUN go build .

FROM debian:latest
RUN apt-get update && apt-get install -y ca-certificates

COPY --from=builder /go/src/app/smokescreen /usr/local/bin/smokescreen
COPY acl.yaml /etc/smokescreen/acl.yaml
COPY config.yaml /etc/smokescreen/config.yaml

EXPOSE 4750
CMD ["smokescreen", "--config-file", "/etc/smokescreen/config.yaml"]
