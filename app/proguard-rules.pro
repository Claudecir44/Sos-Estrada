# Regras do R8 (otimização/ofuscação do release) — ver app/build.gradle.kts.

# Linhas reais nos relatórios do Crashlytics (o mapeamento vai junto no .aab
# e é enviado ao Crashlytics pelo plugin).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Firestore converte documento <-> classe por REFLECTION (toObject/set),
# casando os nomes dos campos com os getters/setters e os próprios campos.
# Renomeados pelo R8, toObject() falha só no release ("No properties to
# serialize" ou "conflicting case" — os dois já apareceram no Caronas).
# Modelo NOVO do Firestore tem que entrar nesta lista.
# ============================================================
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-keep class com.cjstudio.sosestrada.Admin { *; }
-keep class com.cjstudio.sosestrada.Mensagem { *; }
-keep class com.cjstudio.sosestrada.Motorista { *; }
-keep class com.cjstudio.sosestrada.PagamentoPrestador { *; }
-keep class com.cjstudio.sosestrada.Prestador { *; }
-keep class com.cjstudio.sosestrada.Solicitacao { *; }

# ShortcutBadger (número no ícone do app) instancia a implementação de
# cada fabricante por reflection.
-keep class me.leolin.shortcutbadger.impl.** { <init>(); }
