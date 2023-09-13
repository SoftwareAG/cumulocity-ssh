import { Injectable, OnInit } from "@angular/core";
import {
  FetchClient,
  IManagedObject,
  IOperation,
  InventoryService,
  OperationService,
} from "@c8y/client";
import { AlertService } from "@c8y/ngx-components";

@Injectable({ providedIn: "root" })
export class SSHService implements OnInit {
  private profilesFilter: object = {
    type: "SSHProfile",
    // paging information will be a part of the response now
    withTotalPages: true,
    pageSize: 1000,
  };

  constructor(
    private inventory: InventoryService,
    private fetch: FetchClient,
    private alert: AlertService,
    private operationService: OperationService
  ) {}

  ngOnInit(): void {
    this.getProfiles();
  }

  profiles: Map<string, IManagedObject>;

  async getProfiles(): Promise<Map<string, IManagedObject>> {
    let profiles: Map<string, IManagedObject> = new Map<
      string,
      IManagedObject
    >();
    (await this.inventory.list(this.profilesFilter)).data.forEach((mo) => {
      profiles.set(mo.id, mo["com_sag_ssh_api_Profile"]);
      profiles.get(mo.id)["name"] = mo.name;
    });
    this.profiles = profiles;
    return profiles;
  }

  async createProfile(name: string) {
    let result = null;
    try {
      let profile: IManagedObject = await (
        await this.fetch.fetch("service/ssh/profile", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
          },
          body: JSON.stringify({ name: name }),
        })
      ).json();
      console.log(profile);
      result = profile["com_sag_ssh_api_Profile"];
      result.id = profile.id;
      result.name = profile.name;
    } catch (e) {
      console.error(e);
      this.alert.danger(`Couldn't create profile ${name}`, e.message);
    }
    return result;
  }

  async saveProfile(
    profileId: string,
    categories,
    properties,
    measurements,
    operations
  ) {
    await this.fetch.fetch(`service/ssh/profile/${profileId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        categories: categories,
        properties: properties,
        measurements: measurements,
        actions: operations,
      }),
    });
  }

  async deleteProfile(profileId: string) {
    this.fetch.fetch(`service/ssh/profile/${profileId}`, {
      method: "DELETE",
    });
  }

  async deleteDevice(deviceId: string) {
    this.fetch.fetch(`service/ssh/device/${deviceId}`, {
      method: "DELETE",
    });
  }

  async loadPropertyValues(deviceId: string, ids: Array<string>) {
    return await (
      await this.fetch.fetch(
        `service/ssh/device/${deviceId}/properties?ids=${ids.join(",")}`
      )
    ).json();
  }

  async executeAction(
    deviceId: string,
    actionId: string,
    parameters,
    description: string
  ) {
    this.operationService.create({
      deviceId: deviceId,
      com_sag_ssh_Action: { actionId: actionId, parameters: parameters },
      description: description,
    });
  }
}
