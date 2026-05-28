# 1. Introdução

## 1.1 Contexto

Sistemas Multi-Agentes (SMA) constituem um dos paradigmas centrais da Inteligência Artificial Distribuída, no qual entidades autônomas — denominadas agentes — interagem em um ambiente compartilhado para alcançar objetivos individuais ou coletivos [1]. Um agente, conforme definido por Wooldridge e Jennings [2], é um sistema computacional situado em um ambiente, capaz de ação autônoma e flexível para atingir seus objetivos de projeto. A flexibilidade abrange três propriedades fundamentais: **reatividade** (resposta a mudanças no ambiente), **proatividade** (comportamento dirigido por objetivos) e **sociabilidade** (interação com outros agentes).

O **Multi-Agent Programming Contest** (MAPC) é uma competição anual organizada desde 2005 pela Clausthal University of Technology, cujo objetivo é investigar o potencial de sistemas baseados em agentes autônomos e descentralizados diante de cenários complexos e competitivos [16]. Na sua 16ª edição (2022), o cenário proposto — **Agents Assemble III** — desafia times de agentes a explorar um grid toroidal, coletar blocos de diferentes tipos em dispensers, transportá-los até goal zones e submetê-los de acordo com padrões especificados pelas tarefas do servidor [14, 23]. A complexidade reside na combinação de visão local limitada, mapa parcialmente observável, tarefas com prazos, normas regulatórias dinâmicas e a necessidade de coordenação entre múltiplos agentes para montar estruturas multi-bloco.

## 1.2 Motivação

O cenário Agents Assemble apresenta um desafio que não pode ser resolvido de forma satisfatória por agentes independentes: a montagem de estruturas multi-bloco exige sincronização espacial e temporal entre agentes, a exploração eficiente do mapa demanda compartilhamento de informação, e a competição por recursos com um time adversário requer alocação dinâmica de tarefas. Essas características fazem do MAPC um *benchmark* natural para os conceitos fundamentais estudados na disciplina PCS 5703 — Sistemas Multi-Agentes: autonomia, coordenação, cooperação, comunicação e organização social [1].

Do ponto de vista teórico, o cenário demanda agentes que operem sob **racionalidade limitada** (*resource-bounded reasoning*): dado o limite de tempo por step e a informação incompleta do ambiente, os agentes não podem recalcular planos ótimos a cada ciclo — precisam de **intenções** que persistam como compromissos parciais, reduzindo o espaço de deliberação [3]. Essa necessidade alinha-se diretamente com o modelo **BDI** (*Belief-Desire-Intention*), que trata intenções não como meros estados mentais deriváveis de crenças e desejos, mas como elementos distintos e funcionais do raciocínio prático [4].

## 1.3 Objetivo

Este trabalho apresenta o **HIVE** (*Hierarchical Intelligent Virtual Ensemble*), um sistema multi-agente desenvolvido para o cenário Agents Assemble do MAPC 2022. O HIVE é implementado integralmente na plataforma **JaCaMo** [6], que unifica:

- **Jason** [8] para agentes BDI programados em AgentSpeak(L);
- **MOISE+** [9] para especificação organizacional com papéis, grupos e normas;
- **CArtAgO** [11] para artefatos compartilhados (mapa, quadro de tarefas, coordenador de squads).

O sistema emprega **15 agentes BDI** organizados em 3 esquadrões autônomos, com um mecanismo de **leilão distribuído** inspirado no Contract Net Protocol [22] para alocação de tarefas, e um **pool universal de soloists** que permite que qualquer agente livre execute tarefas de forma autônoma. A navegação utiliza A* adaptado para grids toroidais, e a exploração segue uma abordagem de fronteira (*frontier-based exploration*).

## 1.4 Contribuições

As principais contribuições deste trabalho são:

1. **Arquitetura MAOP completa para o MAPC 2022**: demonstração da viabilidade de um SMA inteiramente baseado em JaCaMo (Jason + MOISE+ + CArtAgO) para o cenário Agents Assemble, incluindo todas as três dimensões da programação orientada a multi-agentes [7];

2. **Organização hierárquica com autonomia local**: modelo organizacional em MOISE+ [10] que combina coordenação por esquadrões com um pool universal de soloists, permitindo que cada agente opere autonomamente quando não alocado a tarefas coletivas;

3. **Análise experimental de escalabilidade**: estudo sistemático do impacto do número de agentes (6, 10, 15, 20) sobre o desempenho e a estabilidade, revelando um gargalo de serialização no framework CArtAgO [11] que afeta a confiabilidade do sistema em cenários de alta concorrência;

4. **Resultados competitivos**: scores de 60–100 pontos (média 77) nas simulações completadas com sucesso, posicionando o HIVE na faixa de times medianos a competitivos do MAPC 2022 [14], com desempenho comparável a implementações declarativas como o time LI(A)RA [19].

## 1.5 Organização do Paper

O restante deste documento está organizado da seguinte forma. A **Seção 2** apresenta uma revisão dos conceitos fundamentais envolvidos — agentes inteligentes, arquitetura BDI, JaCaMo, MOISE+, CArtAgO, Contract Net e o cenário MAPC. A **Seção 3** detalha a arquitetura do sistema HIVE, incluindo a visão geral, a arquitetura interna dos agentes, os artefatos de ambiente e os fluxos de coordenação. A **Seção 4** descreve a metodologia, incluindo a abordagem MAOP, a especificação organizacional, os algoritmos de navegação e o protocolo de avaliação. A **Seção 5** apresenta os resultados experimentais — desempenho, otimizações, escalabilidade e diagnóstico de instabilidade. A **Seção 6** discute as lições aprendidas, limitações, trabalhos futuros e conclui o trabalho.
