# 6. Discussão e Conclusão

## 6.1 Lições Aprendidas

O desenvolvimento do HIVE proporcionou insights sobre a aplicação prática de conceitos de SMA em um cenário competitivo real. As lições mais relevantes são discutidas a seguir.

### 6.1.1 O modelo BDI é adequado, mas impõe custos

A arquitetura BDI [3, 4] mostrou-se conceitualmente adequada para o cenário Agents Assemble: as intenções como compromissos persistentes evitaram replanejamento excessivo, e a separação entre crenças, desejos e intenções facilitou a modularização do código em AgentSpeak(L) [8]. No entanto, a natureza interpretada do ciclo de raciocínio do Jason — que unifica planos contra a base de crenças a cada step — introduz um overhead que frameworks compilados (como Java puro) não possuem. Esse overhead se manifestou criticamente quando 15 agentes precisavam deliberar simultaneamente dentro do limite de 8 segundos imposto pelo servidor.

Essa observação é coerente com a experiência do LTI-USP [20], que também utilizou Jason e reportou desafios similares de desempenho no MAPC 2020/2021. O trade-off entre **expressividade declarativa** e **eficiência computacional** permanece como questão fundamental na engenharia de SMA baseados em BDI.

### 6.1.2 A organização MOISE+ estrutura, mas não garante coordenação efetiva

O modelo MOISE+ [9, 10] forneceu uma estrutura organizacional clara — papéis, grupos, esquemas funcionais e normas deônticas — que facilitou o design top-down do sistema. A divisão em 3 esquadrões com autonomia local demonstrou o conceito de **organização descentralizada**: cada líder toma decisões de alocação independentemente, sem necessidade de consenso global.

Contudo, a especificação organizacional em MOISE+ é primariamente **declarativa e estática**: ela define *quem pode fazer o quê*, mas não *quando* ou *como* a coordenação deve ocorrer em tempo de execução. A coordenação dinâmica real — como decidir qual agente pega qual task, ou quando um agente deve abandonar uma tarefa — precisou ser implementada inteiramente nos planos AgentSpeak e nos artefatos CArtAgO, não no modelo organizacional. Isso sugere que, para cenários altamente dinâmicos como o MAPC, a organização MOISE+ funciona melhor como **arcabouço estrutural** do que como **mecanismo de coordenação operacional**.

### 6.1.3 CArtAgO: poder expressivo com gargalo de serialização

Os artefatos CArtAgO [11, 13] demonstraram alto poder expressivo como abstração de ambiente compartilhado: o `SharedMap` encapsulou A*, exploração por fronteira e gerenciamento de obstáculos em uma interface limpa para os agentes; o `TaskBoard` implementou o leilão distribuído sem que os agentes precisassem conhecer detalhes do protocolo. Essa separação de responsabilidades valida a premissa central do meta-modelo A&A [13] — artefatos como ferramentas que mediam a atividade coletiva dos agentes.

Porém, a **serialização de operações por artefato** [12] revelou-se o principal gargalo do sistema. Com 15 agentes gerando ~225 operações de artefato por step (percepção de obstáculos, atualização de mapa, consultas de posição), o tempo de processamento serializado frequentemente excedia o timeout do servidor. Esse problema é arquitetural: o CArtAgO garante consistência sequencial por design, uma propriedade essencial para artefatos transacionais, mas inadequada para operações de alta frequência e baixa contenção como `mark_obstacle`.

Uma solução prática seria a **partição funcional**: separar o `SharedMap` em artefatos menores (e.g., um para obstáculos, outro para dispensers, outro para goal zones), distribuindo a carga de serialização. Essa abordagem não foi implementada no HIVE por restrições de tempo, mas representa uma direção concreta de melhoria.

### 6.1.4 O Contract Net adaptado funciona para tasks simples

O mecanismo de leilão inspirado no Contract Net Protocol [22] provou-se eficaz para alocação de tasks de 1 bloco: a avaliação baseada em distância Manhattan com wrapping toroidal seleciona o soloist mais próximo do dispenser, minimizando tempo de coleta. A adaptação — onde qualquer agente livre pode atuar como *contractor*, independentemente de seu papel organizacional — mostrou-se superior à delegação estrita dentro de cada esquadrão, aumentando significativamente a utilização dos agentes.

Para tasks multi-bloco, entretanto, o protocolo de coordenação (*connect*) revelou fragilidades: a necessidade de sincronização espacial entre assembler e collector em posições adjacentes, combinada com a dificuldade de navegação em mapas cave densos, resultou em taxa de sucesso muito baixa. Este é o principal fator que separa o HIVE dos times de melhor desempenho no MAPC 2022 [14].

## 6.2 Limitações

As limitações identificadas no HIVE são:

1. **Instabilidade por timeout em cascata**: Aproximadamente 46% dos runs não completam os 800 steps devido a timeouts que evoluem para loops de reconexão. A causa raiz é a serialização de operações CArtAgO em mapas densos, e não um defeito na lógica dos agentes;

2. **Tasks multi-bloco pouco exploradas**: O protocolo *connect* para montagem de estruturas de 2+ blocos raramente produz submissões bem-sucedidas, limitando o score a tasks de 1 bloco (10 pontos cada);

3. **Self-assignment sem diversificação**: A auto-atribuição de tarefas por agentes ociosos utiliza a primeira task disponível na base de crenças, sem mecanismo de dispersão — tentativas de usar `findall` com seleção posicional causaram timeouts adicionais;

4. **Mapa estático de obstáculos**: O A* utiliza obstáculos com decay temporal de 30 steps, mas não detecta mudanças causadas por clear events, podendo gerar rotas inválidas;

5. **Ausência de adaptação ao adversário**: O HIVE não modela nem reage ao comportamento do time oponente — uma simplificação aceitável para testes locais mas que reduziria a competitividade em um torneio real.

## 6.3 Trabalhos Futuros

Com base nas lições aprendidas e limitações identificadas, as seguintes direções de pesquisa e desenvolvimento são propostas:

1. **Sharding de artefatos**: Particionar o `SharedMap` em múltiplos artefatos por função (obstáculos, dispensers, goal zones) para reduzir a contenção de serialização, potencialmente eliminando o gargalo de timeout;

2. **Pipeline multi-bloco robusto**: Redesenhar o protocolo *connect* com meeting points dinâmicos, tolerância a falhas de sincronização e replanning automático quando o parceiro de connect não chega a tempo;

3. **Auto-atribuição com afinidade espacial**: Implementar um mecanismo leve de dispersão onde cada agente seleciona tasks com base em um hash de sua posição, sem usar `findall` — garantindo diversificação sem overhead computacional;

4. **Operações CArtAgO assíncronas**: Investigar extensões ao modelo CArtAgO que permitam operações não-bloqueantes para atualizações de mapa, mantendo a serialização apenas para operações transacionais (leilão, alocação);

5. **Aprendizado por reforço para parâmetros**: Utilizar técnicas de RL para otimizar automaticamente parâmetros como frequência de self-assignment, thresholds de stuck detection e limites de timeout — atualmente ajustados manualmente por tentativa e erro.

## 6.4 Conclusão

Este trabalho apresentou o HIVE, um sistema multi-agente baseado em JaCaMo [6] para o cenário Agents Assemble do MAPC 2022 [14]. O sistema demonstra a aplicação integrada dos três pilares da programação orientada a multi-agentes: agentes BDI em Jason [8], organização normativa em MOISE+ [9] e ambiente compartilhado em CArtAgO [11].

Os resultados experimentais — scores de 60–100 pontos com média de 77 — posicionam o HIVE na faixa de times medianos a competitivos do MAPC, validando a viabilidade do paradigma MAOP [7] para cenários complexos de coordenação multi-agente. A análise de escalabilidade revelou que 15 agentes é a configuração ótima, equilibrando cobertura de mapa e overhead de coordenação.

A contribuição principal deste trabalho vai além dos resultados numéricos: a experiência de projetar, implementar, depurar e otimizar um SMA completo em JaCaMo expôs de forma concreta os trade-offs teorizados na literatura — entre autonomia e coordenação [1], entre expressividade declarativa e eficiência [8], entre consistência e concorrência [12]. Esses trade-offs, longe de serem limitações abstratas, manifestaram-se como decisões de engenharia com impacto direto e mensurável no desempenho competitivo do sistema.

Conforme argumentado por Bratman [3], agentes racionais em contextos de recursos limitados precisam de intenções — compromissos parciais que reduzem o espaço de deliberação. O HIVE demonstra que essa necessidade se aplica não apenas aos agentes individualmente, mas ao **sistema como um todo**: a escolha de framework, a granularidade dos artefatos e a frequência de coordenação são, elas mesmas, compromissos de projeto que determinam o sucesso ou fracasso do SMA em ambientes competitivos e dinâmicos.
