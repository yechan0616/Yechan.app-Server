# pg_dump must match the server major version.
FROM postgres:18-alpine
RUN apk add --no-cache rclone age tzdata
COPY scheduler.sh backup.sh restore.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/scheduler.sh /usr/local/bin/backup.sh /usr/local/bin/restore.sh \
    && mkdir -p /backups
VOLUME /backups
HEALTHCHECK --interval=10m --timeout=10s --start-period=1m \
    CMD test -f /backups/.last-success && test -z "$(find /backups/.last-success -mmin +1560)"
ENTRYPOINT ["/usr/local/bin/scheduler.sh"]
