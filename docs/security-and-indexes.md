# Firestore y Storage: reglas sugeridas

## Firestore
```txt
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{email} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.token.email == email;
    }

    match /chats/{chatId} {
      allow read, write: if request.auth != null &&
        request.auth.token.email in resource.data.members;

      match /messages/{messageId} {
        allow read, write: if request.auth != null &&
          request.auth.token.email in get(/databases/$(database)/documents/chats/$(chatId)).data.members;
      }
    }
  }
}
```

## Cloud Storage
```txt
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /chats/{chatId}/images/{file} {
      allow read, write: if request.auth != null &&
        request.auth.token.email in get(/databases/(default)/documents/chats/$(chatId)).data.members;
    }
  }
}
```

## Índices requeridos
Crea los índices compuestos desde la consola de Firebase:

1. **Colección `chats`**
   * Campos: `members` (array contains), `lastTimestamp` (desc).
2. **Colección `chats/{chatId}/messages`**
   * Campos: `timestamp` (asc), `localTimestamp` (asc).
3. **Colección `chats/{chatId}/messages`** (para paginación inversa si se usa)
   * Campos: `timestamp` (desc), `localTimestamp` (desc).

Sin estos índices las consultas usadas por la app devolverán errores (`FAILED_PRECONDITION: The query requires an index`).
