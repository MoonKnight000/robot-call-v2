# livekit-bridge — docs/LIVEKIT.md. cgo for libopus (gopkg.in/hraban/opus.v2); the
# nolibopusfile tag leaves out the Ogg file reader this bridge never uses.
FROM golang:1.24-alpine AS build
RUN apk add --no-cache gcc musl-dev pkgconfig opus-dev
# go mod tidy may need a newer toolchain than the image's when a dependency asks for one.
ENV GOTOOLCHAIN=auto CGO_ENABLED=1
WORKDIR /src
COPY go.mod go.sum* ./
COPY *.go ./
# Resolves the dependencies when go.sum is not committed yet; with it, only verifies them.
RUN go mod tidy && go build -tags nolibopusfile -trimpath -ldflags="-s -w" -o /out/livekit-bridge .

FROM alpine:3.21
RUN apk add --no-cache opus ca-certificates
COPY --from=build /out/livekit-bridge /usr/local/bin/livekit-bridge
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/livekit-bridge"]
