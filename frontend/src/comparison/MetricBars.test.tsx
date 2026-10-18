import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { MetricBars } from './MetricBars';

describe('MetricBars', () => {
  it('rotula A y B, muestra los valores y la evaluación con icono y texto', () => {
    render(<MetricBars data={{
      name: 'travel_time_mean', unit: 's', base: 124, candidate: 30.8,
      percentChange: -75.2, assessment: 'IMPROVED',
    }} />);

    expect(screen.getByText('Tiempo medio de viaje')).toBeInTheDocument();
    expect(screen.getByText('A')).toBeInTheDocument();
    expect(screen.getByText('B')).toBeInTheDocument();
    expect(screen.getByText('124 s')).toBeInTheDocument();
    expect(screen.getByText(/Mejora\s+−75,2 %/)).toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAccessibleName('Tiempo medio de viaje: A 124 s, B 30,8 s');
  });

  it('en lotes dibuja la dispersión y la incluye en la descripción accesible', () => {
    const { container } = render(<MetricBars data={{
      name: 'wait_time_mean', unit: 's', base: 40, candidate: 38, baseSpread: 3, candidateSpread: 2.5,
      percentChange: -5, assessment: 'INCONCLUSIVE',
    }} />);

    expect(container.querySelectorAll('g.spread')).toHaveLength(2);
    expect(screen.getByRole('img')).toHaveAccessibleName('Espera media: A 40 s ± 3 s, B 38 s ± 2,5 s');
    expect(screen.getByText(/No concluyente/)).toBeInTheDocument();
  });
});
