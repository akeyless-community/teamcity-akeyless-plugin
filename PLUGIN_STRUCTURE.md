# TeamCity Akeyless Plugin Structure

## Project Structure

```
teamcity-akeyless-plugin/
├── build.gradle                    # Gradle build configuration
├── settings.gradle                 # Gradle settings
├── teamcity-plugin.xml             # Plugin descriptor
├── LICENSE                         # Apache License 2.0
├── gradlew                         # Gradle wrapper script
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── common/                         # Shared code between server and agent
│   └── src/main/kotlin/com/akeyless/teamcity/common/
│       └── AkeylessConstants.kt
├── server/                         # Server-side components
│   └── src/main/
│       ├── kotlin/com/akeyless/teamcity/server/
│       │   ├── AkeylessConnector.kt              # Main API client
│       │   ├── AkeylessOAuthProvider.kt           # OAuth connection provider
│       │   ├── AkeylessParameterProvider.kt       # Build parameter provider
│       │   ├── AkeylessRemoteParameter.kt         # Remote parameter provider
│       │   └── AkeylessBuildStartProcessor.kt     # Build start processor
│       └── resources/
│           ├── META-INF/build-server-plugin-akeyless.xml  # Spring config
│           └── buildServerResources/
│               ├── editAkeylessConnection.jsp     # Configuration UI
│               └── images/
│                   ├── pluginIcon.svg             # Plugin logo (light)
│                   └── pluginIcon_dark.svg        # Plugin logo (dark)
└── agent/                          # Agent-side components
    └── src/main/
        ├── kotlin/com/akeyless/teamcity/agent/
        │   └── AkeylessAgentSupport.kt
        └── resources/
            └── META-INF/build-agent-plugin-akeyless.xml
```

## Key Components

### Common Module
- **AkeylessConstants**: Plugin ID, API endpoints, authentication method constants, and parameter types

### Server Module

#### Core Components
1. **AkeylessConnector**: Main API client that communicates with Akeyless via the official Java SDK
   - Handles authentication (Access Key, K8s, AWS IAM, Azure AD, GCP, Certificate)
   - Retrieves static, dynamic, and rotated secret values
   - Validates API URLs and secret paths

2. **AkeylessOAuthProvider**: Registers the plugin as an OAuth connection type in TeamCity
   - Provides the configuration UI (JSP)
   - Validates connection properties per auth method

3. **AkeylessBuildStartProcessor**: Resolves `akeyless:` parameter references at build start
   - Authenticates with Akeyless using the configured connection
   - Fetches secrets and injects them as shared build parameters

4. **AkeylessRemoteParameter**: Resolves remote parameters referencing Akeyless secrets
   - Supports `akeyless:secret-path` format for remote parameter queries

5. **AkeylessParameterProvider**: Exposes `akeyless:` parameter names so they are available on agents

### Agent Module
- **AkeylessAgentSupport**: Agent-side lifecycle hook (server handles all secret resolution)

## Configuration Flow

1. User adds an Akeyless connection via **Project Settings > Connections**
2. User configures API URL and authentication method
3. User adds parameters referencing Akeyless secrets using `akeyless:secret-path` format
4. On build start:
   - Server authenticates with Akeyless
   - Server retrieves requested secrets
   - Secrets passed to build agent as shared parameters
   - Build can access secrets as environment variables or parameters
