FROM golang:1.22-alpine AS builder

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="-s -w" -o /out/runner-worker .

FROM alpine:3.20

RUN apk add --no-cache ca-certificates docker-cli tzdata

WORKDIR /app
COPY --from=builder /out/runner-worker /app/runner-worker

ENV RUN_REQUESTED_QUEUE=benchlab.run.requested.q
ENV BENCHLAB_API_BASE_URL=http://api:8080
ENV TMPDIR=/opt/benchlab/worker-tmp
ENV WORKER_HEALTH_ADDRESS=:8081

RUN mkdir -p /opt/benchlab/worker-tmp

EXPOSE 8081

ENTRYPOINT ["/app/runner-worker"]
