FROM --platform=$TARGETPLATFORM alpine:3.20
ARG REDIS_VERSION=7.2.16
RUN apk add --no-cache build-base linux-headers curl tar file
RUN curl --fail --location --silent --show-error \
      "https://download.redis.io/releases/redis-${REDIS_VERSION}.tar.gz" \
      --output /tmp/redis.tar.gz \
    && mkdir /src \
    && tar -xzf /tmp/redis.tar.gz -C /src \
    && cd "/src/redis-${REDIS_VERSION}" \
    && make -j2 MALLOC=libc LDFLAGS=-static redis-server redis-cli \
    && mkdir -p /output \
    && cp src/redis-server src/redis-cli /output/ \
    && cp COPYING /output/REDIS-LICENSE \
    && file /output/redis-server /output/redis-cli
