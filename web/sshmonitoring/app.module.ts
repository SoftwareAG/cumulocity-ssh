import { NgModule } from "@angular/core";
import { BrowserAnimationsModule } from "@angular/platform-browser/animations";
import { RouterModule as NgRouterModule } from "@angular/router";
import {
  CoreModule,
  RouterModule,
  BootstrapComponent,
} from "@c8y/ngx-components";
import { BsModalRef } from "ngx-bootstrap/modal";
import { SSHModule } from "./ssh/ssh.module";

@NgModule({
  imports: [
    BrowserAnimationsModule,
    RouterModule.forRoot(),
    NgRouterModule.forRoot([], {
      enableTracing: false,
      useHash: true,
    }),
    CoreModule.forRoot(),
    SSHModule,
  ],
  providers: [BsModalRef],
  bootstrap: [BootstrapComponent],
})
export class AppModule {}
