FROM python:3.12-alpine

RUN apk add --no-cache gettext

WORKDIR /app
COPY . .
RUN chmod +x entrypoint.sh

EXPOSE 80
ENTRYPOINT ["./entrypoint.sh"]
