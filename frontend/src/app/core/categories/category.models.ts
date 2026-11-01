export type Swatch = 'TEAL' | 'SKY' | 'INDIGO' | 'VIOLET' | 'PINK' | 'ROSE' | 'ORANGE' | 'AMBER' | 'LIME' | 'SLATE';

export interface Category {
  id: string;
  name: string;
  swatch: Swatch;
  itemCount: number;
}

export interface CategoryRequest {
  name: string;
  swatch: Swatch;
}

export interface SwatchStyle {
  label: string;
  background: string;
  text: string;
}

/** Category swatch palette (MASTER §2.3). Domain data: the only raw hex values allowed outside the tokens file. */
export const SWATCHES: Record<Swatch, SwatchStyle> = {
  TEAL: { label: 'Teal', background: '#99F6E4', text: '#134E4A' },
  SKY: { label: 'Sky', background: '#BAE6FD', text: '#0C4A6E' },
  INDIGO: { label: 'Indigo', background: '#C7D2FE', text: '#312E81' },
  VIOLET: { label: 'Violet', background: '#DDD6FE', text: '#4C1D95' },
  PINK: { label: 'Pink', background: '#FBCFE8', text: '#831843' },
  ROSE: { label: 'Rose', background: '#FECDD3', text: '#881337' },
  ORANGE: { label: 'Orange', background: '#FED7AA', text: '#7C2D12' },
  AMBER: { label: 'Amber', background: '#FDE68A', text: '#78350F' },
  LIME: { label: 'Lime', background: '#D9F99D', text: '#365314' },
  SLATE: { label: 'Slate', background: '#E2E8F0', text: '#1E293B' },
};

export const SWATCH_KEYS = Object.keys(SWATCHES) as Swatch[];

export function itemCountLabel(count: number): string {
  if (count === 0) {
    return 'No todos';
  }
  return count === 1 ? '1 item' : `${count} items`;
}
