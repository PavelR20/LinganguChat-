# Plan de mejora para LinganguChat

## 1. Diagnóstico rápido del repositorio
- Proyecto Android en Kotlin con Jetpack Compose y Material 3.
- Autenticación y chats 1 a 1 con Firebase Auth y Firestore ya bocetados en `AuthViewModel`, `ChatViewModel`, `Message.kt`, `DrawerContent` y `ChatScreen`.
- No existe todavía un flujo para compartir imágenes: falta integración con Firebase Storage y componentes de selección/previsualización en la UI.
- El dominio actual es únicamente 1 a 1 (solo `sender`/`receiver`); no hay entidades ni lógica para grupos.
- Gradle declara el BOM de Firebase después de dependencias concretas (por ejemplo `firebase-analytics`), lo cual produce errores de resolución como "Could not find firebase-auth-ktx:".
- Las consultas de Firestore no usan `orderBy(timestamp)` ni índices compuestos; la estructura más robusta sería `chats/{chatId}/messages` usando `FieldValue.serverTimestamp()`.

## 2. Plan de mejora
### A. Dependencias y soporte de imágenes
- Mover la declaración del BOM de Firebase al inicio del bloque `dependencies {}` y dejar las bibliotecas de Firebase sin versión explícita.
- Añadir Firebase Storage, Coil y Activity Result API para selección de imágenes (Photo Picker a partir de Android 13).
- Sugerencia de bloque en `app/build.gradle.kts`:
  ```kotlin
  implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
  implementation("com.google.firebase:firebase-analytics-ktx")
  implementation("com.google.firebase:firebase-auth-ktx")
  implementation("com.google.firebase:firebase-firestore-ktx")
  implementation("com.google.firebase:firebase-storage-ktx")

  implementation(platform("androidx.compose:compose-bom:2024.06.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-ktx:1.9.0")
  implementation("androidx.activity:activity-compose:1.9.0")

  implementation("io.coil-kt:coil-compose:2.6.0")
  ```

### B. Modelo de datos escalable (1 a 1 y grupos)
- Usar la colección `chats` con subcolección `messages`.
- Documento `chats/{chatId}`:
  ```json
  {
    "type": "direct" | "group",
    "name": "...", // solo para grupos
    "members": ["email1", "email2", ...],
    "lastMessage": "...",
    "lastTimestamp": serverTimestamp()
  }
  ```
- Documento `chats/{chatId}/messages/{messageId}`:
  ```json
  {
    "sender": "email",
    "text": "...", // opcional
    "imageUrl": "...", // opcional
    "timestamp": serverTimestamp(),
    "readBy": ["email1", "email2", ...]
  }
  ```
- Chats directos: `chatId` determinístico generado con `hash(sorted([emailA, emailB]).join("_"))`.
- Grupos: `chatId` aleatorio, mínimo tres miembros y nombre obligatorio.

### C. Envío de mensajes de texto e imagen
- `sendText` crea documentos en `chats/{chatId}/messages` con `FieldValue.serverTimestamp()`.
- `sendImage` usa Photo Picker, sube a Firebase Storage (`/chats/{chatId}/images/{uuid}.jpg`), obtiene `downloadUrl` y lo guarda en `imageUrl` (dejando `text` nulo).
- La UI debe mostrar imágenes con `AsyncImage` (Coil), limitar tamaño máximo y aplicar esquinas redondeadas.

### D. Gestión de grupos
- Pantalla “Nuevo grupo”: seleccionar usuarios de `/users`, ingresar nombre y crear doc en `chats`.
- Listado de chats: `whereArrayContains("members", currentUser)` + `orderBy("lastTimestamp", DESC)`.
- Suscripción a mensajes: `orderBy("timestamp", ASC)`.

### E. Reglas y rendimiento
- Utilizar `FieldValue.serverTimestamp()` y paginar mensajes (`limit(50) + startAfter`).
- Configurar reglas de seguridad que restringen lectura/escritura a miembros del chat.
- Crear índices compuestos sugeridos por Firestore.

## 3. Prompt listo para Codex
**Título:** Refactor y features para chat con grupos e imágenes (Firebase + Compose)

```text
Contexto del repo actual:

Proyecto Android Kotlin (Jetpack Compose, Material 3).

Ya existen AuthViewModel, ChatViewModel, ChatScreen, DrawerContent, Message.kt.

Falta soporte para grupos y envío de imágenes. Hay errores de dependencias Firebase.

Objetivo general:

Corregir dependencias Firebase y configurar BOM correctamente.

Implementar chat por grupos con modelo chats/{chatId} + subcolección messages.

Implementar envío de imágenes usando Firebase Storage y mostrarlas en Compose.

Ajustar UI/UX (lista de chats, mensaje, input bar, picker de imágenes, creación de grupos).

Añadir reglas, índices y buenas prácticas (serverTimestamp, orderBy, paginación).

Tareas concretas (paso a paso):

Gradle

En app/build.gradle.kts, mueve implementation(platform("com.google.firebase:firebase-bom:33.1.2")) al inicio de dependencies {} y referencia:

firebase-analytics-ktx, firebase-auth-ktx, firebase-firestore-ktx, firebase-storage-ktx sin versión.

Asegura activity-compose, coil-compose y BOM de Compose. Elimina duplicados.

Modelo de Firestore

Crea la colección chats y subcolección messages como se describe arriba.

Para directos: función para obtener/crear chatId determinístico a partir de dos emails.

Para grupos: función createGroupChat(name, members) que valida members.size >= 3 y setea type="group".

Envió de mensajes

Implementa en ChatViewModel:

suspend fun sendText(chatId: String, text: String, sender: String)
suspend fun sendImage(chatId: String, uri: Uri, sender: String) // sube a Storage y guarda imageUrl
fun subscribeMessages(chatId: String): Flow<List<Message>>

Cada envío debe actualizar chats/{chatId}.lastMessage y lastTimestamp.

Storage

Subir imágenes a: chats/{chatId}/images/{UUID}.jpg con compresión si es necesario.

Obtener downloadUrl y guardarlo en imageUrl del mensaje, text=null.

UI (Compose)

En ChatScreen:

Agrega un IconButton de “clip” o “imagen” dentro del input bar que abra el Photo Picker (PickVisualMedia).

Renderiza mensajes:

Si text != null → burbuja texto.

Si imageUrl != null → AsyncImage(model = imageUrl, contentDescription = null, modifier = ... ) con esquina redondeada y tamaño máx.

Ordena mensajes por timestamp asc. Muestra separadores de fecha si cambia el día.

Crea pantalla “Lista de chats” (drawer o nueva screen) que haga query:

db.collection("chats")
  .whereArrayContains("members", currentUser)
  .orderBy("lastTimestamp", Query.Direction.DESCENDING)

y muestre:

avatar genérico (iniciales),

nombre del chat (name o el del contacto para directos),

preview de lastMessage y hora corta.

Crea pantalla “Nuevo grupo”: lista de usuarios desde /users con checkboxes, input name, botón “Crear”.

Permisos / Photo Picker

Usa Android Photo Picker con ActivityResultContracts.PickVisualMedia.

Para Android 13+ no pidas READ_EXTERNAL_STORAGE. Para versiones viejas, maneja permiso legacy.

Rules y seguridad

Firestore rules (borrador):

match /databases/{db}/documents {
  match /users/{uid} {
    allow read: if request.auth != null;
    allow write: if request.auth != null && request.auth.token.email == uid;
  }
  match /chats/{chatId} {
    allow read, write: if request.auth != null
      && request.auth.token.email in resource.data.members;
    match /messages/{msgId} {
      allow read, write: if request.auth != null
        && request.auth.token.email in get(/databases/$(db)/documents/chats/$(chatId)).data.members;
    }
  }
}

Storage rules (borrador) restringidas a miembros del chat (usa rutas y verifica pertenencia por Firestore).

Rendimiento

Paginación de mensajes (limit(50) + startAfter).

Usa serverTimestamp() y siempre orderBy("timestamp").

Maneja estado con StateFlow y collectAsStateWithLifecycle.

Pruebas

Caso feliz: texto + imagen en 1-a-1 y en grupo.

Permiso denegado / cancelado: picker cancelado → no crashear.

Latencia: mostrar “subiendo imagen…” con un LinearProgressIndicator.

Sincronía: dos usuarios ven el mismo chat y los mensajes aparecen en tiempo real.

Entregables esperados por Codex:

Cambios en build.gradle.kts.

Nuevas funciones en ChatViewModel (sendText, sendImage, subscribeMessages).

Nuevas pantallas/Composables: Lista de chats, Nuevo grupo, mejoras en ChatScreen (picker + render de imágenes).

Utilidades: generador de chatId, helpers de Storage.

Actualizaciones en AuthViewModel para asegurar creación de /users con email, name.

Snippets de Firestore/Storage rules y nota de índices necesarios.

Criterio de aceptación:

Puedo crear un grupo, enviar/recibir texto e imágenes tanto en 1-a-1 como en grupos; las imágenes cargan con Coil; las listas de chats se actualizan con el último mensaje; no hay errores de dependencias; la app no se cierra si cancelo el picker.
```

## 4. Sugerencias de diseño en Compose
- Pantalla principal con dos pestañas: "Chats" y "Contactos" y un FAB para crear nuevos chats o grupos.
- Burbujas de mensajes con esquinas de 16 dp, alineadas a la derecha si el remitente soy yo y a la izquierda para el resto; timestamp pequeño abajo a la derecha.
- Imágenes como miniaturas de máximo 220 dp de ancho, `contentScale = Crop` y `AsyncImage` con `placeholder`/`loading indicator`.
- Separadores de fecha como chips centrados: "Hoy", "Ayer", "3 oct 2025".
- Estados de entrega opcionales: check gris (enviado), doble check (entregado), doble check azul (leído).
- Mantener `LinganguChatTheme` con variantes claro/oscuro y asegurar contraste adecuado y áreas táctiles ≥ 48 dp.
