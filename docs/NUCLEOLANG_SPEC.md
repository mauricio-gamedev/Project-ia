# NúcleoLang — Initial Language Specification 0.2 (Alpha)

NúcleoLang is an original language under development for deterministic scripts and potential integration with Núcleo AI. The name is a working title; trademark availability and possible naming conflicts have not been evaluated.

## Product direction

NúcleoLang is intended to be general and extensible rather than limited to a fixed list of project types. The core defines language semantics and execution; libraries, host capabilities, and future backends may connect it to additional environments. AI-generated code or suggestions do not grant permissions: external capabilities must be registered by the host application.

Long-term goals include apps, games, utilities, services, and multiple compilation targets. These are compatibility goals, not features already available in the alpha. Model intelligence is an optional layer; language semantics should remain deterministic.

## Version 0.2 principles

- Independent Kotlin implementation; no copying of parser, AST, or runtime source from reference projects.
- Deterministic interpreter without eval, reflection, dynamic JVM class loading, shell, network, or direct filesystem access.
- Side effects and external integration are available only through native functions explicitly registered by the host app. The core includes print, len, push, pop, and contains; host calls use NucleoHostFunction and return only supported language values.
- Collections are runtime values with configurable limits intended to protect mobile memory and stability.
- Limits apply to source size, token count, execution steps, and call depth.
- Errors aim to include line/column information when a source location is available.

## Example

    fun fibonacci(n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    let total = fibonacci(8);
    if (total == 21) {
        print("NucleoLang is running");
    }

## Simplified grammar

    program       -> declaration* EOF
    declaration   -> letDecl | funDecl | statement
    letDecl       -> "let" IDENTIFIER "=" expression ";"
    funDecl       -> "fun" IDENTIFIER "(" parameters? ")" block
    statement     -> "if" "(" expression ")" statement ("else" statement)?
                   | "while" "(" expression ")" statement
                   | "return" expression? ";"
                   | block
                   | expression ";"
    block         -> "{" declaration* "}"
    expression    -> assignment
    assignment    -> logicOr ("=" assignment)?
    logicOr       -> logicAnd ("||" logicAnd)*
    logicAnd      -> equality ("&&" equality)*
    equality      -> comparison (("==" | "!=") comparison)*
    comparison    -> term (("<" | "<=" | ">" | ">=") term)*
    term          -> factor (("+" | "-") factor)*
    factor        -> unary (("*" | "/" | "%") unary)*
    unary         -> ("!" | "-") unary | call
    call          -> primary (("(" arguments? ")") | ("[" expression "]"))*
    primary       -> NUMBER | STRING | true | false | null | IDENTIFIER | listLiteral | "(" expression ")"
    listLiteral   -> "[" (expression ("," expression)*)? "]"

## Runtime semantics

- Primitive values: number (Double), text, boolean, and null.
- Conditions in if/while and operands of &&, ||, and ! require booleans; no implicit coercion is performed.
- + accepts two numbers or two strings. Other arithmetic operators require numbers.
- Variables use lexical scope. Duplicate declarations in the same scope are errors.
- Mutable lists support literals such as [1, 2], access like values[0], assignment such as values[0] = 9, nested lists, and chained indexing. Indices must be non-negative integers within the valid range.
- Initial collection functions: len(value), push(list, value), pop(list), and contains(collectionOrText, value). push returns the new length; pop removes and returns the last list item.
- Functions are callable values with positional parameters. Recursion is supported within a bounded call depth.
- Host functions are registered by the app and cannot override built-in names. Arbitrary JVM objects are rejected as runtime values.
- return is valid only within a function. A function without explicit return produces null.
- && and || short-circuit.
- Execution is bounded by a step budget. The current default is 100,000 steps; the configurable maximum is 1,000,000. Default and maximum call depth are 64.
- Current safety limits include source length up to 256,000 characters, up to 50,000 tokens, syntactic nesting to 128 levels, up to 32 parameters/arguments, and up to 10,000 list items by default (configurable up to a 100,000 hard ceiling).

## Known limitations

Version 0.2 alpha has list collections but does not yet include maps/dictionaries, classes/interfaces, module imports, static typing, async/await, bytecode compilation, a debugger, formatter, or direct integration with engineering tools and AI models. Host functions are trusted Kotlin code supplied by the app; the language does not load arbitrary user plugins. Before running untrusted programs in production, the project needs cooperative UI cancellation, stronger per-task resource isolation, and additional security tests.

## Roadmap

1. Continuous regression suite for lexer/parser/runtime and a versioned normative spec.
2. Maps/dictionaries, structures/types, and ergonomic iteration.
3. Importable packages/modules with reproducible resolution and license metadata.
4. Capability API with declared permissions for files, processes, network, and project tools.
5. Cooperative cancellation, structured diagnostics, debugger, and formatter.
6. Safe agent and Project Factory integration.
7. Addable compilation backends per target; evaluate bytecode/WASM/native options after stabilizing semantics.
