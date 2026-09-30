import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';

import { AlertSettings } from '../../../core/models/notification.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { AlertsCard } from './alerts-card';

const URL = '/api/clients/current/alerts';
const DEFAULTS: AlertSettings = {
  lowBalanceThreshold: null,
  largeMovementThreshold: null,
  loginAlerts: true,
  emailAlerts: true,
};

describe('AlertsCard', () => {
  let httpTesting: HttpTestingController;

  async function render(settings: AlertSettings = DEFAULTS) {
    await TestBed.configureTestingModule({
      imports: [AlertsCard],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AlertsCard);
    fixture.detectChanges();
    httpTesting.expectOne(URL).flush(settings);
    await fixture.whenStable();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const toggle = (id: string) =>
      loader.getHarness(MatSlideToggleHarness.with({ selector: `#${id}` }));
    const save = async () => {
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.save')!.click();
      await fixture.whenStable();
    };
    return { fixture, el: fixture.nativeElement as HTMLElement, toggle, save };
  }

  afterEach(() => httpTesting.verify());

  it('shows the saved settings', async () => {
    const { el, toggle } = await render({
      ...DEFAULTS,
      lowBalanceThreshold: 1500,
      emailAlerts: false,
    });
    expect(await (await toggle('lowBalanceOn')).isChecked()).toBe(true);
    expect(el.querySelector<HTMLInputElement>('#lowBalance')!.value).toBe('1500');
    expect(await (await toggle('largeMovementOn')).isChecked()).toBe(false);
    expect(el.querySelector('#largeMovement')).toBeNull();
    expect(await (await toggle('loginAlerts')).isChecked()).toBe(true);
    expect(await (await toggle('emailAlerts')).isChecked()).toBe(false);
  });

  it('turns on a threshold and saves the whole settings', async () => {
    const { el, toggle, save } = await render();
    await (await toggle('largeMovementOn')).toggle();
    typeInto(el, '#largeMovement', '20000');
    await (await toggle('loginAlerts')).toggle();
    await save();

    const req = httpTesting.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      lowBalanceThreshold: null,
      largeMovementThreshold: 20000,
      loginAlerts: false,
      emailAlerts: true,
    });
    req.flush({ ...DEFAULTS, largeMovementThreshold: 20000, loginAlerts: false });
  });

  it('requires a valid amount while a threshold is on, and drops it when turned off', async () => {
    const { el, toggle, save } = await render();
    await (await toggle('lowBalanceOn')).toggle();
    await save();
    httpTesting.expectNone(URL);
    expect(el.textContent).toContain('Ingresá un monto mayor a cero');

    typeInto(el, '#lowBalance', '10.123');
    await save();
    httpTesting.expectNone(URL);

    await (await toggle('lowBalanceOn')).toggle(); // off again: the invalid amount no longer matters
    await (await toggle('emailAlerts')).toggle();
    await save();
    expect(httpTesting.expectOne(URL).request.body.lowBalanceThreshold).toBeNull();
  });
});
