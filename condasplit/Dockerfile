FROM --platform=$BUILDPLATFORM golang:1.27 AS build
ARG TARGETARCH
WORKDIR /src
COPY . .
RUN go test ./... \
 && CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w" -o /condasplit . \
 && cd frontend && go test ./... \
 && CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w" -o /condasplit-frontend .

FROM scratch
COPY --from=build /condasplit /condasplit-frontend /
# the image is also the BuildKit frontend of the conda/micromamba:v2-fast template, see frontend/
ENTRYPOINT ["/condasplit-frontend"]
