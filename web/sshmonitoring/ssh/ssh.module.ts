import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import {
  CoreModule,
  FormsModule,
  HOOK_NAVIGATOR_NODES,
  HOOK_ROUTE,
  ModalModule,
  ViewContext,
} from "@c8y/ngx-components";
import {
  ProfileCellRendererComponent,
  SSHRegistrationComponent,
} from "./registration/ssh-registration.component";
import { SSHConfigurationComponent } from "./configuration/ssh-configuration.component";
import { SSHNavigationFactory } from "./factories/Navigation";
import { FormlyModule } from "@ngx-formly/core";
import { PanelWrapperComponent } from "./panel-wrapper.component";
import { RepeatTypeComponent } from "./repeat-section.type";
import { SSHDeviceComponent } from "./device/ssh-device.component";
import { SSHGuard } from "./device/ssh.guard";
import { DragDropModule } from "@angular/cdk/drag-drop";
import { DeviceGridModule } from "@c8y/ngx-components/device-grid";
import { CollapseModule } from "ngx-bootstrap/collapse";

/**
 * Angular Routes.
 * Within this array at least path (url) and components are linked.
 */
const routes: Routes = [
  {
    path: "ssh-devices",
    component: SSHRegistrationComponent,
  },
  {
    path: "ssh-profiles",
    component: SSHConfigurationComponent,
  },
];

@NgModule({
  declarations: [
    SSHRegistrationComponent,
    SSHConfigurationComponent,
    RepeatTypeComponent,
    PanelWrapperComponent,
    SSHDeviceComponent,
    ProfileCellRendererComponent,
  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule.forChild(routes),
    CoreModule,
    ModalModule,
    DragDropModule,
    DeviceGridModule,
    CollapseModule,
    FormlyModule.forRoot({
      types: [{ name: "repeat", component: RepeatTypeComponent }],
      wrappers: [{ name: "panel", component: PanelWrapperComponent }],
    }),
  ],
  providers: [
    {
      provide: HOOK_NAVIGATOR_NODES,
      useClass: SSHNavigationFactory,
      multi: true,
    },
    {
      provide: HOOK_ROUTE,
      useValue: [
        {
          context: ViewContext.Device,
          path: "ssh_config",
          component: SSHDeviceComponent,
          label: "SSH",
          priority: 100,
          icon: "remote-desktop1",
          canActivate: [SSHGuard],
        },
      ],
      multi: true,
    },
  ],
})
export class SSHModule {}
