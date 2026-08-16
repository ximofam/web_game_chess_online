# FAQ

**Q: How does the Guest login work?**
A: A guest user relies on a fallback authentication flow where the system creates a temporary account and issues a token. The frontend treats this as a `GUEST` role and restricts certain features like profile editing.

**Q: Why do WebSockets disconnect sometimes?**
A: WebSockets might drop due to network instability. The `SocketProvider` automatically notifies the user via toasts. A manual reconnect option is generally provided.

**Q: What happens if an API call fails?**
A: The `GlobalApiErrorHandler` and individual page catch blocks manage errors by showing global toasts to keep the user informed.

**Q: How is internationalization handled?**
A: The frontend uses `react-i18next`. All user-facing text is translated dynamically. Missing translations fallback to the default language or display the key.
