# NúcleoLang — especificação inicial 0.2 (alfa)

NúcleoLang é uma linguagem autoral em desenvolvimento para scripts determinísticos e automações que possam integrar o Núcleo AI. O nome é um codinome de trabalho; marca e colisões de nome ainda precisam ser verificadas.

## Direção do produto

NúcleoLang é uma linguagem geral e extensível em evolução, não uma lista fechada de tipos de software que ela pode produzir. O core define semântica e execução; bibliotecas, capacidades do host e futuros backends adicionam integração com novos ambientes. O fato de a IA sugerir código ou ferramentas não concede permissões por si só: todas as capacidades externas precisam ser registradas pelo aplicativo hospedeiro.

O objetivo de longo prazo inclui aplicações, jogos, ferramentas, serviços e múltiplos destinos de compilação. Esses são objetivos de compatibilidade, não recursos já disponíveis na versão alfa. A inteligência do modelo é uma camada opcional; a semântica básica da linguagem deve permanecer determinística.

## Princípios da versão 0.2

- Implementação independente em Kotlin, sem copiar parser, AST ou runtime dos arquivos de referência.
- Interpretador determinístico, sem `eval`, reflexão, carregamento dinâmico de classes, shell, rede ou acesso direto a arquivos.
- Efeitos colaterais e integrações externas somente por funções nativas explicitamente registradas pelo aplicativo hospedeiro. O core fornece `print`, `len`, `push`, `pop` e `contains`; funções de host usam `NucleoHostFunction` e retornam apenas valores admitidos pela linguagem.
- Coleções são valores do runtime, limitadas por configuração para proteger memória e estabilidade em dispositivos móveis.
- Limites de tamanho da fonte, tokens, etapas de execução e profundidade de chamadas.
- Erros úteis com linha e coluna quando a posição de origem está disponível.

## Sintaxe de exemplo

```nuc
fun fibonacci(n) {
    if (n <= 1) return n;
    return fibonacci(n - 1) + fibonacci(n - 2);
}

let total = fibonacci(8);
if (total == 21) {
    print("NúcleoLang funcionando");
}
```

## Gramática resumida

```text
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
equality     -> comparison (("==" | "!=") comparison)*
comparison   -> term (("<" | "<=" | ">" | ">=") term)*
term          -> factor (("+" | "-") factor)*
factor        -> unary (("*" | "/" | "%") unary)*
unary         -> ("!" | "-") unary | call
call          -> primary (("(" arguments? ")") | ("[" expression "]"))*
primary       -> NUMBER | STRING | true | false | null | IDENTIFIER | listLiteral | "(" expression ")"
listLiteral   -> "[" (expression ("," expression)*)? "]"
```

## Regras semânticas

- Tipos primitivos: número (`Double`), texto, booleano e `null`.
- Condições de `if`/`while` e operandos de `&&`, `||` e `!` exigem booleanos; não há coerção implícita.
- `+` aceita dois números ou dois textos. Outros operadores aritméticos exigem números.
- Variáveis usam escopo léxico. Uma declaração duplicada no mesmo escopo gera erro.
- Listas mutáveis: literais `[1, 2]`, acesso `values[0]`, atribuição `values[0] = 9`, listas aninhadas e indexação encadeada. Índices precisam ser inteiros não negativos dentro dos limites.
- Biblioteca inicial de coleções: `len(textoOuLista)`, `push(lista, valor)`, `pop(lista)` e `contains(listaOuTexto, valor)`. `push` retorna o novo tamanho; `pop` remove e retorna o último item.
- Funções são valores chamáveis com parâmetros posicionais e suportam recursão com limite de profundidade.
- Funções hospedeiras são registradas pela aplicação e não podem substituir nomes embutidos. O runtime recusa retorno de objetos JVM arbitrários.
- `return` só é válido dentro de uma função. Funções sem retorno explícito retornam `null`.
- `&&` e `||` usam curto-circuito.
- A execução tem orçamento de etapas por programa. O padrão atual é 100.000 passos; máximo configurável de 1.000.000. Profundidade padrão e máxima de chamadas: 64.
- Limites atuais: fonte até 256.000 caracteres, até 50.000 tokens, aninhamento sintático até 128 níveis, 32 parâmetros/argumentos e até 10.000 elementos por lista por padrão (configurável até um teto de segurança de 100.000). Execução e recursão também têm orçamentos configuráveis dentro dos tetos do runtime.

## Limitações conhecidas

A versão 0.2 alfa já inclui listas, mas ainda não inclui mapas/dicionários, classes/interfaces, importação de módulos, tipos estáticos, async/await, compilador/bytecode, depurador, formatador ou integração direta com ferramentas de engenharia e modelos de IA. As funções de host são código Kotlin confiável fornecido pelo app; a linguagem não carrega plugins arbitrários do usuário. Antes de executar programas não confiáveis em produção, precisamos adicionar cancelamento cooperativo na UI, isolamento/limites de recursos por tarefa e testes de segurança adicionais.

## Roadmap

1. Sistema de regressão contínua para lexer/parser/runtime e especificação normativa.
2. Mapas/dicionários, tipos/estruturas e iteração ergonômica.
3. Pacotes e módulos importáveis com resolução reproduzível e metadados de licença.
4. API de capacidades com permissões declaradas para arquivos, processos, rede e ferramentas de projeto.
5. Cancelamento cooperativo, diagnósticos estruturados, depurador e formatador.
6. Integração segura com agente de IA e Project Factory.
7. Backends de compilação adicionáveis por destino; avaliar bytecode/WASM/nativo após estabilizar a semântica.
