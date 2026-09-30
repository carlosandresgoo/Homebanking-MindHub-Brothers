import { Clipboard } from '@angular/cdk/clipboard';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Account } from '../../../core/models/account.model';
import { provideTestDefaults } from '../../../testing/providers';
import { ReceiveCard } from './receive-card';

const ACCOUNT: Account = {
  id: 7,
  number: 'VIN001',
  cbu: '9990001800000000000017',
  alias: 'melba.ahorros',
  creationDate: '2026-08-30T10:00:00',
  balance: 5000,
};

describe('ReceiveCard', () => {
  async function render(editable: boolean) {
    await TestBed.configureTestingModule({
      imports: [ReceiveCard],
      providers: provideTestDefaults(),
    }).compileComponents();
    const copy = vi.spyOn(TestBed.inject(Clipboard), 'copy').mockReturnValue(true);
    const fixture = TestBed.createComponent(ReceiveCard);
    fixture.componentRef.setInput('account', ACCOUNT);
    fixture.componentRef.setInput('editable', editable);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement, copy };
  }

  it('shows the CBU split in blocks, the alias and the account number', async () => {
    const { el } = await render(false);
    expect(el.querySelector('.cbu')?.textContent).toContain('99900018 00000000000017');
    expect(el.querySelector('.alias')?.textContent).toContain('melba.ahorros');
    expect(el.textContent).toContain('VIN001');
  });

  it('copies the CBU without spaces and the alias', async () => {
    const { el, copy } = await render(false);
    el.querySelector<HTMLButtonElement>('.copy-cbu')!.click();
    expect(copy).toHaveBeenCalledWith('9990001800000000000017');
    el.querySelector<HTMLButtonElement>('.copy-alias')!.click();
    expect(copy).toHaveBeenCalledWith('melba.ahorros');
  });

  it('shares all the data (copied when the browser cannot share)', async () => {
    const { el, copy } = await render(false);
    Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('Compartir'))!
      .click();
    const text = copy.mock.calls[0][0];
    expect(text).toContain('CBU: 9990001800000000000017');
    expect(text).toContain('Alias: melba.ahorros');
  });

  it('lets only the owner change the alias and reports the new one', async () => {
    expect((await render(false)).el.querySelector('.edit-alias')).toBeNull();
    TestBed.resetTestingModule();

    const { fixture, el } = await render(true);
    const updated = { ...ACCOUNT, alias: 'sol.rio.mate' };
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(updated),
    } as ReturnType<MatDialog['open']>);
    const changed = vi.fn();
    fixture.componentInstance.aliasChanged.subscribe(changed);

    el.querySelector<HTMLButtonElement>('.edit-alias')!.click();
    expect(open.mock.calls[0][1]?.data).toEqual({ account: ACCOUNT });
    expect(changed).toHaveBeenCalledWith(updated);
  });
});
