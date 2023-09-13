import { Injectable } from "@angular/core";
import { CanActivate, ActivatedRouteSnapshot } from "@angular/router";
import { Observable } from "rxjs";

@Injectable({ providedIn: "root" })
export class SSHGuard implements CanActivate {
  canActivate(
    route: ActivatedRouteSnapshot
  ): Observable<boolean> | Promise<boolean> | boolean {
    const contextData = route.data.contextData || route.parent.data.contextData;
    return contextData.com_sag_ssh_api_Configuration != undefined;
  }
}
