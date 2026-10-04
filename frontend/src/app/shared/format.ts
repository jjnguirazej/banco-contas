import { Pipe, PipeTransform } from '@angular/core';

const money = new Intl.NumberFormat('pt-PT', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
  useGrouping: 'always', // em pt-PT, sem isto 1500 aparece sem separador de milhares
});
const dateTime = new Intl.DateTimeFormat('pt-PT', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'Africa/Maputo',
});
const dateOnly = new Intl.DateTimeFormat('pt-PT', { dateStyle: 'medium', timeZone: 'Africa/Maputo' });

/** 1250.5 → "1 250,50" */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value == null ? '' : money.format(value);
  }
}

/** Data e hora no fuso de Moçambique. Com o argumento 'data', só a data. */
@Pipe({ name: 'when' })
export class WhenPipe implements PipeTransform {
  transform(value: string | null | undefined, mode: 'data' | 'dataHora' = 'dataHora'): string {
    if (!value) return '';
    const d = new Date(value);
    return mode === 'data' ? dateOnly.format(d) : dateTime.format(d);
  }
}

const TYPE_LABELS: Record<string, string> = {
  ORDEM: 'À ordem',
  POUPANCA: 'Poupança',
  SALDO_INICIAL: 'Saldo inicial',
  TRANSFERENCIA_DEBITO: 'Transferência enviada',
  TRANSFERENCIA_CREDITO: 'Transferência recebida',
};

@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? (TYPE_LABELS[value] ?? value) : '';
  }
}
