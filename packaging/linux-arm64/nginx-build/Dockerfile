FROM jddbms-arm64-frontend:local
ARG NGINX_VERSION=1.30.5
RUN apk add --no-cache build-base linux-headers curl file pcre2-dev zlib-dev zlib-static
RUN curl --fail --location --silent --show-error \
      "https://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz" --output /tmp/nginx.tar.gz \
    && mkdir /src \
    && tar -xzf /tmp/nginx.tar.gz -C /src \
    && cd "/src/nginx-${NGINX_VERSION}" \
    && ./configure --prefix=/opt/jddbms --with-http_gzip_static_module --with-ld-opt=-static \
    && make -j2 \
    && mkdir -p /output \
    && cp objs/nginx /output/nginx \
    && cp LICENSE /output/NGINX-LICENSE \
    && curl --fail --location --silent --show-error \
      https://raw.githubusercontent.com/PCRE2Project/pcre2/pcre2-10.43/LICENCE --output /output/PCRE2-LICENSE \
    && curl --fail --location --silent --show-error \
      https://raw.githubusercontent.com/madler/zlib/v1.3.2/LICENSE --output /output/ZLIB-LICENSE \
    && file /output/nginx \
    && /output/nginx -v
