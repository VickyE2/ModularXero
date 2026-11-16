# MODULAR - X

___

### YML FORMAT
Should be placed in the resource folder with the `module.yml` file_name
```yaml
main: location_of_the_module_class e.g fam.toks.questionables.modules.AppleModule
module_name: name_of_said_module # module identifier
mavenDeps:
  - xxx.ooo.uuu:some-dep:0.0.4
  - mymy.ooo.ty:some-other-dep2.8.9
  - akari.the.cutie:genshin-dep:7.18.9
  - hana.the.granny:betrayal-strategy:18.1.23
embeddedLibs: # these will all be extracted to a common modules-lib as a single non nested jar.
  - embedded/some_embedded_lib.jar
  - how_to_cook.jar
  - ignore_messages_library-ari-0.0.2.jar
authors: 
  - Akari
  - Toki
  - SomePigga
  
# api level declaration
# exact single API
api: 2

# range shorthand
api: 2-4

# block style
api:
  min: 2
  max: 4

# legacy single keys (also supported)
apiMin: 2
apiMax: 4
```
Or you could use the new and improved annotation method that only ONE class should have:
```kotlin
@ModuleEntry(
    moduleName = "apple",
    authors = ["Hana","Luna"],
    mavenDeps = ["com.example:lib:1.2.3"],
    embeddedLibs = ["embedded/some_embedded_lib.jar"],
    description = "An apple-y module",
    min = 1, max = 3
)
class AppleModule : ARCModule { ... }
```
___

### MODULES TYPES

### [InternalModule](src/main/kotlin/com/vicky/modularxero/Module.kt#L154):
> This is the base module that cannot be accessed outside the Module.kt file. It is made to allow for abstraction and set things. It can also not be implemented outside the Module.kt scope.

### [AbstractModule](src/main/kotlin/com/vicky/modularxero/Module.kt#L45):
> This is the base module that should not be extended by other sub-classes as it is internal.

### [WeirdlyAbstractModule](src/main/kotlin/com/vicky/modularxero/Module.kt#L68):
> This is the base module that can be extended by other sub-classes.

### [AbstractSystemModule](src/main/kotlin/com/vicky/modularxero/Module.kt#L81):
> These modules are special as they dont use networking to communicate but rather a global [ClassBasedCommunicator](src/main/kotlin/com/vicky/modularxero/Module.kt#L233) accessed from [here](src/main/kotlin/com/vicky/modularxero/Module.kt#L19).

### [AbstractServerModule](src/main/kotlin/com/vicky/modularxero/Module.kt#L192):
> These modules unlike native modules accept messages by using a port from the servers main ip address.

---
### BUNDLED SYSTEM MODULES

### [EmailModule](src/main/java/com/vicky/modularxero/modules/email/EmailModule.java):
> A module for faster sending of email services. an example of how it works:
 ```kotlin
val reply = classCommunicator.requestBlocking(
    Message(
        server_name, DefaultServices.EMAIL_SERVICE.id,
        EmailModule.Services.SEND_MAIL.code,
        MapValueBuilder()
            .put("sender", gmail)
            .put("password", password)
            .put("receiver", email)
            .put("template", EmailTemplate.passwordResetTemplate(COLOR_SCHEME))
            .put(
                "subject_map",
                MapValueBuilder()
                    .put("appName", SomePeculiarServiceName)
                    .build()
            )
            .put(
                "body_map",
                MapValueBuilder()
                    .put("appName", SomePeculiarServiceName)
                    .put("userName", email)
                    .put("resetLink", code)
                    .put("expiryMinutes", DEFAULT_CODE_DURATION)
                    .put("supportEmail", gmail)
                    .put("sentAt", LocalDateTime.now())
                    .build()
            )
            .build()
    ), 20_000L
)
if (reply == null || !reply.payload.getBoolean("was_sent")!!) {
     return ResponseHelper.error(
        MessageType.FORGOT_PASSWORD,
        "Failed to password reset email. Please again try later.",
        id
    )
}
```
> The email templates [EmailTemplate](src/main/kotlin/com/vicky/modularxero/email/EmailHelper.kt#L48), were added for easier writing of email html's. you could always just make yours but this list will be getting frequent updates.