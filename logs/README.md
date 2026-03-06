# Application Logs

This directory contains the application log files.

## Log Files

- **application.log** - Current log file
- **application-YYYY-MM-DD.N.log** - Archived log files (rolled daily or when reaching 10MB)

## Configuration

Logging is configured in `src/main/resources/logback-spring.xml` with the following settings:

### Log Levels
- **Root**: WARN
- **com.airepublic.t1**: INFO (application logs)
- **org.springframework**: WARN
- **org.springframework.ai**: INFO

### Rolling Policy
- **Max File Size**: 10MB per file
- **Max History**: 30 days
- **Total Size Cap**: 1GB
- **Pattern**: `application-%d{yyyy-MM-dd}.%i.log`

### Log Format
```
YYYY-MM-DD HH:mm:ss.SSS [thread-name] LEVEL logger-name - message
```

Example:
```
2026-03-05 09:00:00.123 [main] INFO  c.a.t1.Application - Application started
```

## Console Output

Console output is **disabled**. All logs are written to files only.

To enable console logging for debugging, modify `src/main/resources/logback-spring.xml` and add a console appender:

```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>${LOG_PATTERN}</pattern>
    </encoder>
</appender>

<root level="WARN">
    <appender-ref ref="FILE"/>
    <appender-ref ref="CONSOLE"/>
</root>
```

## Viewing Logs

### Tail the log file (Linux/Mac/Git Bash):
```bash
tail -f logs/application.log
```

### View on Windows (PowerShell):
```powershell
Get-Content logs\application.log -Wait -Tail 50
```

### Search logs:
```bash
grep "ERROR" logs/application.log
grep "Agent" logs/application.log
```

## Log Rotation

Logs automatically rotate when:
1. The current file reaches 10MB
2. A new day starts

Old log files are automatically deleted after 30 days.

## Troubleshooting

If logs are not being created:
1. Check that the application has write permissions to the `logs/` directory
2. Verify `logback-spring.xml` exists in `src/main/resources/`
3. Check for errors during application startup

## Git Ignore

Log files are automatically ignored by Git (see `.gitignore`).
Only this README and `.gitkeep` are tracked in version control.
