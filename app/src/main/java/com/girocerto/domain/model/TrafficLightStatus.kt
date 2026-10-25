package com.girocerto.domain.model

/**
 * Representa a classificação visual tipo semáforo para tomada de decisão rápida.
 */
enum class TrafficLightStatus {
    GREEN,   // Oferta recomendada: compatível com as metas e com lucro saudável.
    YELLOW,  // Oferta em atenção: próxima dos limites ou atende parcialmente às metas.
    RED,     // Oferta não recomendada: abaixo dos limites aceitáveis ou com prejuízo.
    INVALID  // Dados insuficientes, incompletos ou inconsistentes para análise.
}
