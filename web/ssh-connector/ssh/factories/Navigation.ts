import { Injectable } from "@angular/core";
import { NavigatorNode, NavigatorNodeFactory, _ } from "@c8y/ngx-components";

@Injectable()
export class SSHNavigationFactory implements NavigatorNodeFactory {
  nav: NavigatorNode[] = [];
  // Implement the get()-method, otherwise the ExampleNavigationFactory
  // implements the NavigatorNodeFactory interface incorrectly (!)
  constructor() {
    let sshDevices: NavigatorNode = new NavigatorNode({
      label: _("All devices"),
      icon: "sensor",
      path: "/ssh-devices",
      priority: 1,
      routerLinkExact: false,
    });

    let sshProfiles: NavigatorNode = new NavigatorNode({
      label: _("Profiles"),
      icon: "c8y-administration",
      path: "/ssh-profiles",
      priority: 2,
      routerLinkExact: false,
    });

    let loraNode: NavigatorNode = new NavigatorNode({
      label: _("Devices"),
      icon: "wifi",
      name: "ssh",
      children: [sshDevices, sshProfiles],
      priority: 1,
      routerLinkExact: false,
    });

    this.nav.push(loraNode);
  }

  get() {
    return this.nav;
  }
}
