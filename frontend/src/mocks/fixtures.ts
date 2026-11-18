/** Dados fixos dos mocks. Tudo aqui é conteúdo de exemplo, em português, coerente com o contrato. */
import type {
  Alternative,
  CorpusSource,
  HardwareInfo,
  JavaVersionInfo,
  ModelInfo,
  QuestionType,
  SourceRef,
} from '@/api/types'

export const HARDWARE: HardwareInfo = {
  totalRamGb: 15.8,
  freeRamGb: 7.4,
  gpu: 'Intel(R) UHD Graphics',
  cpuOnly: true,
}

export function createModels(): ModelInfo[] {
  return [
    {
      id: 'low',
      kind: 'chat',
      label: 'Baixo',
      description: 'Respostas rápidas para dúvidas simples.',
      ollamaModel: 'qwen2.5:3b',
      remoteHost: null,
      sizeGb: 1.9,
      minRamGb: 3,
      installed: true,
      loaded: false,
      fits: true,
      supportsTools: true,
      available: true,
      unavailableReason: null,
      recommended: false,
      speed: 'fast',
      measuredTokensPerSecond: 9.1,
      diskUsageGb: 1.9,
    },
    {
      id: 'medium',
      kind: 'chat',
      label: 'Médio',
      description: 'Uso geral: equilíbrio entre qualidade e velocidade.',
      ollamaModel: 'qwen2.5:7b',
      remoteHost: null,
      sizeGb: 4.4,
      minRamGb: 6,
      installed: true,
      loaded: false,
      fits: true,
      supportsTools: true,
      available: true,
      unavailableReason: null,
      recommended: true,
      speed: 'medium',
      measuredTokensPerSecond: 2.3,
      diskUsageGb: 4.4,
    },
    {
      id: 'strong',
      kind: 'chat',
      label: 'Forte',
      description: 'Provas em PDF e código complexo; mais lento, mais preciso.',
      ollamaModel: 'qwen2.5:14b',
      remoteHost: null,
      sizeGb: null,
      minRamGb: 10,
      installed: false,
      loaded: false,
      fits: true,
      supportsTools: null,
      available: false,
      unavailableReason: 'NOT_INSTALLED',
      recommended: false,
      speed: 'slow',
      measuredTokensPerSecond: null,
      diskUsageGb: null,
    },
    {
      id: 'extra',
      kind: 'chat',
      label: 'Extra',
      description: 'Máxima precisão, com raciocínio. Pede mais memória do que esta máquina tem.',
      ollamaModel: 'qwen2.5:32b',
      remoteHost: null,
      sizeGb: null,
      minRamGb: 22,
      installed: false,
      loaded: false,
      fits: false,
      supportsTools: null,
      available: false,
      unavailableReason: 'DOES_NOT_FIT',
      recommended: false,
      speed: 'very_slow',
      measuredTokensPerSecond: null,
      diskUsageGb: null,
    },
    {
      id: 'embedding',
      kind: 'embedding',
      label: 'Embeddings',
      description: 'Modelo de busca semântica na documentação (RAG).',
      ollamaModel: 'bge-m3',
      remoteHost: null,
      sizeGb: 1.2,
      minRamGb: 2,
      installed: true,
      loaded: false,
      fits: true,
      supportsTools: null,
      available: true,
      unavailableReason: null,
      recommended: false,
      speed: 'fast',
      measuredTokensPerSecond: null,
      diskUsageGb: 1.2,
    },
  ]
}

/** Tamanho do download simulado de cada modelo ainda não instalado (GB). */
export const PULL_SIZE_GB: Record<string, number> = { strong: 9.0, extra: 19.5 }

export const JAVA_VERSIONS: JavaVersionInfo[] = [
  {
    version: 4,
    label: 'Java 1.4',
    releaseYear: 2002,
    lts: false,
    highlights: ['assert', 'NIO', 'Expressões regulares'],
  },
  {
    version: 5,
    label: 'Java 5',
    releaseYear: 2004,
    lts: false,
    highlights: ['Generics', 'Enums', 'Anotações', 'For-each'],
  },
  {
    version: 8,
    label: 'Java 8',
    releaseYear: 2014,
    lts: true,
    highlights: ['Lambdas', 'Stream API', 'Optional', 'java.time'],
  },
  {
    version: 11,
    label: 'Java 11',
    releaseYear: 2018,
    lts: true,
    highlights: ['var em lambdas', 'HttpClient', 'String.isBlank'],
  },
  {
    version: 17,
    label: 'Java 17',
    releaseYear: 2021,
    lts: true,
    highlights: ['Records', 'Sealed classes', 'Text blocks'],
  },
  {
    version: 21,
    label: 'Java 21',
    releaseYear: 2023,
    lts: true,
    highlights: ['Virtual threads', 'Pattern matching para switch', 'Record patterns'],
  },
  {
    version: 25,
    label: 'Java 25',
    releaseYear: 2025,
    lts: true,
    highlights: ['Scoped values', 'Classes compactas', 'Módulos de importação'],
  },
  {
    version: 27,
    label: 'Java 27',
    releaseYear: 2026,
    lts: false,
    highlights: ['Recursos em prévia da próxima LTS'],
  },
]

export function createSources(): CorpusSource[] {
  const base = (
    id: string,
    name: string,
    kind: CorpusSource['kind'],
    baseUrl: string,
    min: number | null,
    max: number | null,
    status: CorpusSource['status'],
    chunkCount: number,
  ): CorpusSource => ({
    id,
    name,
    kind,
    baseUrl,
    licenseTerms: 'Uso local e pessoal; não redistribuir.',
    javaVersionMin: min,
    javaVersionMax: max,
    status,
    chunkCount,
    lastIngestedAt: status === 'READY' ? '2026-11-10T22:00:00Z' : null,
    errorMessage: status === 'FAILED' ? 'Servidor da documentação não respondeu' : null,
  })
  return [
    base(
      '5e5e5e5e-1111-4222-8333-6f6f6f6f6f01',
      'Javadoc Java 21',
      'JAVADOC',
      'https://docs.oracle.com/en/java/javase/21/docs/api/',
      21,
      21,
      'READY',
      12840,
    ),
    base(
      '5e5e5e5e-1111-4222-8333-6f6f6f6f6f02',
      'Javadoc Java 8',
      'JAVADOC',
      'https://docs.oracle.com/javase/8/docs/api/',
      8,
      8,
      'READY',
      9120,
    ),
    base(
      '5e5e5e5e-1111-4222-8333-6f6f6f6f6f03',
      'JLS 17',
      'JLS',
      'https://docs.oracle.com/javase/specs/jls/se17/html/',
      17,
      17,
      'READY',
      2310,
    ),
    base(
      '5e5e5e5e-1111-4222-8333-6f6f6f6f6f04',
      'JEPs (OpenJDK)',
      'JEP',
      'https://openjdk.org/jeps/',
      null,
      null,
      'PENDING',
      0,
    ),
    base(
      '5e5e5e5e-1111-4222-8333-6f6f6f6f6f05',
      'Tutoriais oficiais',
      'TUTORIAL',
      'https://dev.oracle.com/java/',
      null,
      null,
      'FAILED',
      0,
    ),
  ]
}

// ------------------------------------------------------------------ fontes
export const SRC_RECORDS: SourceRef = {
  title: 'JEP 395: Records',
  url: 'https://openjdk.org/jeps/395',
  snippet: 'Records são classes que atuam como portadoras transparentes de dados imutáveis.',
  kind: 'JEP',
  javaVersionMin: 16,
  javaVersionMax: null,
}
export const SRC_RECORDS_DOC: SourceRef = {
  title: 'Javadoc: java.lang.Record',
  url: 'https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html',
  snippet: 'Classe base comum de todas as classes record.',
  kind: 'JAVADOC',
  javaVersionMin: 16,
  javaVersionMax: null,
}
export const SRC_STREAMS: SourceRef = {
  title: 'Javadoc: java.util.stream.Stream',
  url: 'https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Stream.html',
  snippet: 'Uma sequência de elementos que suporta operações agregadas sequenciais e paralelas.',
  kind: 'JAVADOC',
  javaVersionMin: 8,
  javaVersionMax: null,
}
export const SRC_GENERICS: SourceRef = {
  title: 'JLS 4.5.1: Type Arguments and Wildcards',
  url: 'https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5.1',
  snippet: 'Os argumentos de tipo podem ser curingas: ?, ? extends T, ? super T.',
  kind: 'JLS',
  javaVersionMin: 5,
  javaVersionMax: null,
}
export const SRC_VTHREADS: SourceRef = {
  title: 'JEP 444: Virtual Threads',
  url: 'https://openjdk.org/jeps/444',
  snippet: 'Threads virtuais são leves e aumentam a vazão de aplicações concorrentes.',
  kind: 'JEP',
  javaVersionMin: 21,
  javaVersionMax: null,
}
export const SRC_STRINGS: SourceRef = {
  title: 'JLS 15.21.3: Reference Equality Operators',
  url: 'https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.21.3',
  snippet: 'O operador == compara referências, não o conteúdo dos objetos.',
  kind: 'JLS',
  javaVersionMin: null,
  javaVersionMax: null,
}

// -------------------------------------------------------- respostas do chat
export interface ChatScenario {
  id: 'records' | 'streams' | 'generics' | 'vthreads' | 'default'
  sources: SourceRef[]
  /** Roda o exemplo no sandbox antes de responder (mostra `executing_code` e `verifying`). */
  runsCode: boolean
  thinking: string[]
  reply: Record<'pt-BR' | 'en', string>
  toolQuery: string
  execOutput?: string
}

const RECORDS_PT = `Um **record** é uma classe de dados imutável, definitiva a partir do **Java 16** ([JEP 395](https://openjdk.org/jeps/395)).

\`\`\`java
public record Ponto(int x, int y) {
    public double distancia() {
        return Math.sqrt(x * x + y * y);
    }
}
\`\`\`

O compilador gera o construtor, os acessores (\`x()\` e \`y()\`), \`equals\`, \`hashCode\` e \`toString\`.

- Os campos são \`final\`: não existe *setter*.
- Um record não estende outra classe, mas pode implementar interfaces.
- Prefira uma classe comum quando o objeto precisar de estado mutável.

Rodei o exemplo no sandbox com Java 21 e a saída confere: \`Ponto[x=3, y=4]\`.`

const RECORDS_EN = `A **record** is an immutable data class, final since **Java 16** ([JEP 395](https://openjdk.org/jeps/395)).

\`\`\`java
public record Point(int x, int y) {
    public double distance() {
        return Math.sqrt(x * x + y * y);
    }
}
\`\`\`

The compiler generates the constructor, accessors (\`x()\`, \`y()\`), \`equals\`, \`hashCode\` and \`toString\`.

- Fields are \`final\`: there are no setters.
- A record cannot extend a class, but it can implement interfaces.

I ran the example in the sandbox on Java 21 and the output matches: \`Point[x=3, y=4]\`.`

const STREAMS_PT = `A **Stream API** (Java 8) descreve *o que* fazer com os dados, em vez de *como* percorrê-los.

\`\`\`java
List<String> nomes = pessoas.stream()
    .filter(p -> p.idade() >= 18)
    .map(Pessoa::nome)
    .sorted()
    .toList();
\`\`\`

- \`filter\`, \`map\` e \`sorted\` são operações **intermediárias** (preguiçosas).
- \`toList()\` (Java 16) é a operação **terminal** que dispara o processamento.
- Um stream **não guarda dados** e só pode ser consumido uma vez.

Para laços simples, o \`for\` continua claro e mais rápido. Use streams quando houver encadeamento de transformações.`

const STREAMS_EN = `The **Stream API** (Java 8) describes *what* to do with data instead of *how* to iterate it.

\`\`\`java
List<String> names = people.stream()
    .filter(p -> p.age() >= 18)
    .map(Person::name)
    .sorted()
    .toList();
\`\`\`

- \`filter\`, \`map\` and \`sorted\` are lazy **intermediate** operations.
- \`toList()\` (Java 16) is the **terminal** operation that triggers processing.
- A stream stores no data and can only be consumed once.`

const GENERICS_PT = `Os curingas (*wildcards*) deixam um método aceitar famílias de tipos. A regra prática é **PECS**: *Producer Extends, Consumer Super*.

\`\`\`java
static double soma(List<? extends Number> origem) { // só lê (produz)
    return origem.stream().mapToDouble(Number::doubleValue).sum();
}

static void preenche(List<? super Integer> destino) { // só escreve (consome)
    destino.add(42);
}
\`\`\`

- \`? extends T\`: você **lê** como \`T\`, mas não pode adicionar elementos.
- \`? super T\`: você **escreve** \`T\`, mas lê apenas como \`Object\`.
- \`?\` sozinho vale para "qualquer tipo", quando o tipo não importa.`

const GENERICS_EN = `Wildcards let a method accept families of types. The rule of thumb is **PECS**: *Producer Extends, Consumer Super*.

\`\`\`java
static double sum(List<? extends Number> source) { // read-only (producer)
    return source.stream().mapToDouble(Number::doubleValue).sum();
}
\`\`\`

- \`? extends T\`: you **read** as \`T\`, but cannot add elements.
- \`? super T\`: you **write** \`T\`, but read only as \`Object\`.`

const VTHREADS_PT = `**Threads virtuais** (Java 21, [JEP 444](https://openjdk.org/jeps/444)) são threads leves, gerenciadas pela JVM, ideais para muitas tarefas que passam o tempo esperando I/O.

\`\`\`java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10_000; i++) {
        executor.submit(() -> chamarServico());
    }
}
\`\`\`

- Criar milhares delas é barato; não faça *pool* de threads virtuais.
- Não ajudam em tarefas que gastam CPU o tempo todo.
- Evite segurar \`synchronized\` durante I/O bloqueante (pode "fixar" a thread de plataforma).`

const VTHREADS_EN = `**Virtual threads** (Java 21, [JEP 444](https://openjdk.org/jeps/444)) are lightweight JVM-managed threads, ideal for many tasks that mostly wait on I/O.

\`\`\`java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> callService());
}
\`\`\`

- Creating thousands is cheap; do not pool them.
- They do not help CPU-bound work.`

const DEFAULT_PT = `Esta é uma resposta **simulada** (mock) do agente: o backend real ainda não está conectado.

Para ver cenários diferentes, pergunte sobre **records**, **streams**, **generics** ou **threads virtuais**.

\`\`\`java
System.out.println("Olá, Java Mind Agent!");
\`\`\`

Dica: escreva \`[erro]\` na pergunta para ver o estado de falha durante o stream.`

const DEFAULT_EN = `This is a **simulated** (mock) agent reply: the real backend is not connected yet.

Ask about **records**, **streams**, **generics** or **virtual threads** to see other scenarios.

\`\`\`java
System.out.println("Hello, Java Mind Agent!");
\`\`\`

Tip: write \`[erro]\` in your question to see the mid-stream failure state.`

const SCENARIOS: ChatScenario[] = [
  {
    id: 'records',
    sources: [SRC_RECORDS, SRC_RECORDS_DOC],
    runsCode: true,
    toolQuery: 'records',
    execOutput: 'Ponto[x=3, y=4]',
    thinking: [
      'O usuário pergunta sobre records. ',
      'Preciso confirmar a partir de qual versão são definitivos (16) e mostrar um exemplo curto. ',
      'Vou rodar o exemplo no sandbox para garantir que a saída está certa.',
    ],
    reply: { 'pt-BR': RECORDS_PT, en: RECORDS_EN },
  },
  {
    id: 'streams',
    sources: [SRC_STREAMS],
    runsCode: false,
    toolQuery: 'stream api',
    thinking: [
      'Pergunta sobre Streams. ',
      'Explicar operações intermediárias e terminal, com um exemplo curto.',
    ],
    reply: { 'pt-BR': STREAMS_PT, en: STREAMS_EN },
  },
  {
    id: 'generics',
    sources: [SRC_GENERICS],
    runsCode: false,
    toolQuery: 'wildcards generics',
    thinking: ['Curingas em generics. ', 'Apresentar a regra PECS com dois exemplos.'],
    reply: { 'pt-BR': GENERICS_PT, en: GENERICS_EN },
  },
  {
    id: 'vthreads',
    sources: [SRC_VTHREADS],
    runsCode: false,
    toolQuery: 'virtual threads',
    thinking: [
      'Threads virtuais exigem Java 21. ',
      'Mostrar o executor por tarefa e as ressalvas.',
    ],
    reply: { 'pt-BR': VTHREADS_PT, en: VTHREADS_EN },
  },
]

const DEFAULT_SCENARIO: ChatScenario = {
  id: 'default',
  sources: [SRC_RECORDS_DOC],
  runsCode: false,
  toolQuery: 'java',
  thinking: ['Pergunta aberta. ', 'Responder de forma curta e sugerir temas para testar o mock.'],
  reply: { 'pt-BR': DEFAULT_PT, en: DEFAULT_EN },
}

export function pickScenario(message: string): ChatScenario {
  const text = message.toLowerCase()
  if (/record/.test(text)) return SCENARIOS[0]
  if (/stream|lambda/.test(text)) return SCENARIOS[1]
  if (/generic|gen[eé]ric|wildcard|curinga/.test(text)) return SCENARIOS[2]
  if (/thread|virtual/.test(text)) return SCENARIOS[3]
  return DEFAULT_SCENARIO
}

// ------------------------------------------------------------ PDF de provas
export interface QuestionSeed {
  label: string
  type: QuestionType
  statement: string
  code: string | null
  alternatives: Alternative[] | null
  javaVersion: number | null
  pageNumber: number
  needsReview: boolean
  answer: {
    chosen: string | null
    explanation: string
    steps: string[]
    sources: SourceRef[]
    verified: boolean
    output: string | null
    confidence: number
  }
}

const tf: Alternative[] = [
  { label: 'V', text: 'Verdadeiro' },
  { label: 'F', text: 'Falso' },
]

export const QUESTION_SEEDS: QuestionSeed[] = [
  {
    label: '1',
    type: 'MULTIPLE_CHOICE',
    statement: 'O que o código abaixo imprime?',
    code: 'int x = 5;\nint y = x++ + ++x;\nSystem.out.println(y);',
    alternatives: [
      { label: 'a', text: '10' },
      { label: 'b', text: '11' },
      { label: 'c', text: '12' },
      { label: 'd', text: 'Erro de compilação' },
    ],
    javaVersion: 8,
    pageNumber: 1,
    needsReview: false,
    answer: {
      chosen: 'c',
      explanation:
        'O pós-incremento devolve 5 e depois x vale 6; o pré-incremento deixa x em 7 e devolve 7. A soma é 12.',
      steps: [
        'x++ avalia para 5 e x passa a 6',
        '++x incrementa para 7 e avalia para 7',
        'y = 5 + 7 = 12',
      ],
      sources: [
        {
          title: 'JLS 15.14.2: Postfix Increment Operator',
          url: 'https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.14.2',
          kind: 'JLS',
        },
      ],
      verified: true,
      output: '12',
      confidence: 0.97,
    },
  },
  {
    label: '2',
    type: 'TRUE_FALSE',
    statement: 'Uma classe declarada como `final` pode ser estendida por uma classe abstrata.',
    code: null,
    alternatives: tf,
    javaVersion: null,
    pageNumber: 2,
    needsReview: false,
    answer: {
      chosen: 'F',
      explanation:
        '`final` impede qualquer herança: nem classes abstratas nem concretas podem estendê-la.',
      steps: ['`final` na classe proíbe subclasses', 'Classe abstrata também é subclasse'],
      sources: [
        {
          title: 'JLS 8.1.1.2: final Classes',
          url: 'https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.1.2',
          kind: 'JLS',
        },
      ],
      verified: false,
      output: null,
      confidence: 0.95,
    },
  },
  {
    label: '3',
    type: 'MULTIPLE_CHOICE',
    statement: 'Qual interface funcional recebe um argumento e devolve um resultado?',
    code: null,
    alternatives: [
      { label: 'a', text: 'Supplier<T>' },
      { label: 'b', text: 'Consumer<T>' },
      { label: 'c', text: 'Function<T, R>' },
      { label: 'd', text: 'Runnable' },
    ],
    javaVersion: 8,
    pageNumber: 3,
    needsReview: true,
    answer: {
      chosen: 'c',
      explanation:
        '`Function<T, R>` tem o método `R apply(T t)`. `Supplier` só devolve, `Consumer` só recebe, `Runnable` não faz nenhum dos dois.',
      steps: [
        'Recebe 1 argumento: Function, Consumer',
        'Devolve resultado: Function, Supplier',
        'Interseção: Function',
      ],
      sources: [
        {
          title: 'Javadoc: java.util.function.Function',
          url: 'https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/function/Function.html',
          kind: 'JAVADOC',
        },
      ],
      verified: false,
      output: null,
      confidence: 0.9,
    },
  },
  {
    label: '4',
    type: 'CODE_OUTPUT',
    statement: 'O que o código abaixo imprime?',
    code: 'String a = "java";\nString b = new String("java");\nSystem.out.println((a == b) + " " + a.equals(b));',
    alternatives: null,
    javaVersion: 11,
    pageNumber: 4,
    needsReview: false,
    answer: {
      chosen: null,
      explanation:
        '`==` compara referências: `new String` cria outro objeto, então é `false`. `equals` compara o conteúdo: `true`.',
      steps: [
        'a aponta para o literal no pool',
        'b é um objeto novo no heap',
        '`==` é false e `equals` é true',
      ],
      sources: [SRC_STRINGS],
      verified: true,
      output: 'false true',
      confidence: 0.98,
    },
  },
  {
    label: '5',
    type: 'MULTIPLE_CHOICE',
    statement: 'Qual recurso virou definitivo no Java 16 para classes de dados imutáveis?',
    code: null,
    alternatives: [
      { label: 'a', text: 'Sealed classes' },
      { label: 'b', text: 'Records' },
      { label: 'c', text: 'Text blocks' },
      { label: 'd', text: 'Pattern matching para switch' },
    ],
    javaVersion: 17,
    pageNumber: 5,
    needsReview: false,
    answer: {
      chosen: 'b',
      explanation:
        'Records foram finalizados no Java 16 (JEP 395). Sealed classes chegaram no 17, text blocks no 15 e o pattern matching para switch no 21.',
      steps: ['JEP 395 → Java 16 → Records'],
      sources: [SRC_RECORDS],
      verified: false,
      output: null,
      confidence: 0.96,
    },
  },
  {
    label: '6',
    type: 'FILL_IN',
    statement:
      'Complete: o método ______ de `Optional` devolve o valor presente ou um valor padrão.',
    code: null,
    alternatives: null,
    javaVersion: 8,
    pageNumber: 6,
    needsReview: true,
    answer: {
      chosen: null,
      explanation:
        '`orElse(T other)` devolve o valor, se existir, ou `other`. Para cálculo preguiçoso do padrão, existe `orElseGet`.',
      steps: ['Optional.orElse(padrão)'],
      sources: [
        {
          title: 'Javadoc: java.util.Optional',
          url: 'https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html',
          kind: 'JAVADOC',
        },
      ],
      verified: false,
      output: null,
      confidence: 0.88,
    },
  },
  {
    label: '7',
    type: 'ESSAY',
    statement: 'Explique a diferença entre `ArrayList` e `LinkedList` e quando usar cada uma.',
    code: null,
    alternatives: null,
    javaVersion: null,
    pageNumber: 7,
    needsReview: false,
    answer: {
      chosen: null,
      explanation:
        '`ArrayList` usa um vetor: acesso por índice em O(1) e inserção no fim amortizada; inserir no meio custa O(n). `LinkedList` usa nós ligados: inserir/remover nas pontas é O(1), mas acessar por índice é O(n) e gasta mais memória. Na prática, prefira `ArrayList`; use `LinkedList` só como fila/deque com muitas inserções nas pontas (e mesmo aí, `ArrayDeque` costuma ser melhor).',
      steps: ['Estrutura interna de cada uma', 'Custo por operação', 'Recomendação prática'],
      sources: [
        {
          title: 'Javadoc: java.util.ArrayList',
          url: 'https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html',
          kind: 'JAVADOC',
        },
      ],
      verified: false,
      output: null,
      confidence: 0.82,
    },
  },
  {
    label: '8',
    type: 'MULTIPLE_CHOICE',
    statement: 'O que o código abaixo imprime em Java 21?',
    code: 'Object o = 42;\nString r = switch (o) {\n    case Integer i when i > 40 -> "grande";\n    case Integer i -> "pequeno";\n    default -> "outro";\n};\nSystem.out.println(r);',
    alternatives: [
      { label: 'a', text: 'grande' },
      { label: 'b', text: 'pequeno' },
      { label: 'c', text: 'outro' },
      { label: 'd', text: 'Erro de compilação' },
    ],
    javaVersion: 21,
    pageNumber: 8,
    needsReview: false,
    answer: {
      chosen: 'a',
      explanation:
        'No Java 21 o `switch` aceita padrões com guarda (`when`). 42 é `Integer` e 42 > 40, então o primeiro `case` vence.',
      steps: [
        'o é Integer(42)',
        'Primeiro case: Integer i when i > 40 → verdadeiro',
        'r = "grande"',
      ],
      sources: [
        {
          title: 'JEP 441: Pattern Matching for switch',
          url: 'https://openjdk.org/jeps/441',
          kind: 'JEP',
          javaVersionMin: 21,
        },
      ],
      verified: true,
      output: 'grande',
      confidence: 0.94,
    },
  },
]

/** Tempo estimado por questão, em segundos, por tier (heurística do mock). */
export const SECONDS_PER_QUESTION: Record<string, number> = {
  low: 25,
  medium: 70,
  strong: 160,
  extra: 400,
}
