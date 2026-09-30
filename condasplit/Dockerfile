FROM --platform=$BUILDPLATFORM golang:1.27 AS build
ARG TARGETARCH
WORKDIR /src
# the frontend dependencies are downloaded first, so that they are cached
COPY frontend/go.mod frontend/go.sum frontend/
RUN cd frontend && go mod download
COPY . .
RUN go test ./... \
 && CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w" -o /condasplit . \
 && cd frontend && go test ./... \
 && CGO_ENABLED=0 GOOS=linux GOARCH=$TARGETARCH go build -trimpath -ldflags="-s -w" -o /condasplit-frontend .

FROM scratch
COPY --from=build /condasplit /condasplit
COPY --from=build /condasplit-frontend /condasplit-frontend
# the image is also the BuildKit frontend of the conda/micromamba:v3 template, see frontend/
ENTRYPOINT ["/condasplit-frontend"]
