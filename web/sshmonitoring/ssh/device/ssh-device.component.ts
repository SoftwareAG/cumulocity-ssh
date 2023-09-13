import { Component, OnInit, TemplateRef, ViewChild } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import {
  IManagedObject,
  IUser,
  InventoryService,
  OperationStatus,
  UserService,
} from "@c8y/client";
import { DeviceOperation } from "../configuration/DeviceOperation";
import {
  DeviceOperationElement,
  ParamType,
} from "../configuration/DeviceOperationElement";
import { FormlyFormOptions, FormlyFieldConfig } from "@ngx-formly/core";
import { SSHService } from "../service/SSHService";
import {
  ActionControl,
  AlertService,
  BuiltInActionType,
  BulkActionControl,
  Column,
  ModalService,
  OperationRealtimeService,
  Pagination,
  Status,
} from "@c8y/ngx-components";
import { BsModalRef, BsModalService } from "ngx-bootstrap/modal";
import { FormGroup } from "@angular/forms";
import * as _ from "lodash";

@Component({
  selector: "ssh-device",
  templateUrl: "./ssh-device.component.html",
})
export class SSHDeviceComponent implements OnInit {
  device: IManagedObject;
  profile;
  form = new FormGroup({});
  parameterValues: any = {};
  options: FormlyFormOptions = {};
  fields: FormlyFieldConfig[][] = new Array<FormlyFieldConfig[]>();
  JSON;
  currentTab; // = this.tabs[0];
  profiles: Map<string, IManagedObject>;
  currentProfile: string;
  currentAction: DeviceOperation;
  @ViewChild("actionModal", { static: false })
  actionModal: TemplateRef<any>;
  actionModalRef: BsModalRef;

  networkColumns: Column[] = [
    { name: "name", header: "Name", path: "name" },
    { name: "ip", header: "IP", path: "ip" },
    { name: "gateway", header: "Gateway", path: "gateway" },
    { name: "status", header: "Status", path: "status" },
  ];

  packageColumns: Column[] = [
    { name: "name", header: "Name", path: "name" },
    { name: "version", header: "Version", path: "version" },
    { name: "update", header: "Update", path: "update" },
  ];
  /** Initial pagination settings. */
  pagination: Pagination = {
    pageSize: 30,
    currentPage: 1,
  };
  /** Will allow for selecting items and perform bulk actions on them. */
  selectable: boolean = true;
  /**
   * Defines actions for individual rows.
   * `type` can be one of the predefined ones, or a custom one.
   * `callback` executes the action (based on the selected item object).
   */
  networkActionControls: ActionControl[] = [
    {
      type: BuiltInActionType.Edit,
      callback: (selectedItem) => this.onNetworkEdit(selectedItem),
    },
    {
      type: BuiltInActionType.Delete,
      callback: (selectedItem) => this.onNetworkDelete(selectedItem),
    },
  ];

  packageActionControls: ActionControl[] = [
    {
      type: "UPGRADE",
      icon: "installing-updates",
      callback: (selectedItem) => this.upgradePackage(selectedItem),
    },
  ];

  bulkPackageActionControls: BulkActionControl[] = [
    {
      type: "UPGRADE",
      icon: "installing-updates",
      callback: (ids) => this.upgradePackages(ids),
    },
  ];

  sshAdmin: boolean = false;
  tables = {};

  constructor(
    private route: ActivatedRoute,
    private inventory: InventoryService,
    private sshService: SSHService,
    private modalService: BsModalService,
    private modalService2: ModalService,
    private userService: UserService,
    private operationsRT: OperationRealtimeService,
    private alert: AlertService
  ) {
    this.JSON = JSON;
    operationsRT.start();
  }

  async ngOnInit(): Promise<void> {
    await this.loadDevice();
    this.profiles = await this.sshService.getProfiles();
    this.currentProfile = this.device.profile;
    this.loadProfile();
    this.isSshAdmin();
    this.operationsRT.onUpdate$(this.device.id).subscribe((op) => {
      console.log(op);
      if (op.com_sag_ssh_Action) {
        let action: DeviceOperation = this.getAction(
          op.com_sag_ssh_Action.actionId
        );
        if (op.status == OperationStatus.FAILED) {
          this.alert.danger("Action " + action.name + " failed.", op.error);
        }
        if (op.status == OperationStatus.SUCCESSFUL) {
          this.alert.success("Action " + action.name + " succeeded", op.result);
        }
      }
    });
  }

  async isSshAdmin() {
    let user: IUser = (await this.userService.current()).data;
    console.log(user);
    this.sshAdmin = this.userService.hasRole(user, "ROLE_SSH_ADMIN");
  }

  async loadProfile() {
    this.profile = this.profiles.get(this.currentProfile);
    this.fields = new Array<FormlyFieldConfig[]>();
    this.profile.actions.forEach((action) => {
      this.fields[action.id] = this.getFields(action);
    });
    this.profile.properties.forEach((prop) => {
      if (prop.json) {
        this.tables[prop.name] = {
          headers: this.getHeaders(this.device[prop.name]),
          actions: this.getActions(prop.name),
        };
      }
    });
    this.currentTab = this.profile.categories[0];
    this.currentTab.active = "active";
  }

  getSimpleActions(category: string) {
    return this.profile.actions.filter(
      (a) => !a.tableRowAction && a.categories.includes(category)
    );
  }

  async changeProfile(profile) {
    this.currentProfile = profile;
    let toUpdate: Partial<IManagedObject> = {
      id: this.device.id,
      profile: profile,
    };
    await this.inventory.update(toUpdate);
    await this.loadDevice();
    await this.loadProfile();
  }

  changeTab(e: Event, tab) {
    e.preventDefault();
    this.currentTab.active = "";
    tab.active = "active";
    this.currentTab = tab;
  }

  filterProperties(category: string) {
    return this.profile.properties.filter((p) =>
      p.categories.includes(category)
    );
  }

  async loadDevice() {
    this.device = (
      await this.inventory.detail(
        this.route.snapshot.parent.data.contextData.id
      )
    ).data;
  }

  getFieldFromElement(element: DeviceOperationElement): FormlyFieldConfig {
    let field: FormlyFieldConfig = {
      key: element.id,
      templateOptions: { label: element.name },
    };

    switch (element.type) {
      case ParamType.STRING:
        field.type = "input";
        field.templateOptions.type = "text";
        break;
      case ParamType.INTEGER:
      case ParamType.FLOAT:
        field.type = "input";
        field.templateOptions.type = "number";
        break;
      case ParamType.BOOL:
        field.type = "checkbox";
        break;
      case ParamType.DATE:
        field.type = "input";
        field.templateOptions.type = "date";
        break;
      case ParamType.ENUM:
        field.type = "radio";
        field.templateOptions.options = element.values.map((e) => {
          return { label: e, value: e };
        });
        break;
      case ParamType.FILE:
        field.type = "input";
        field.templateOptions.type = "file";
        break;
      case ParamType.GROUP:
        if (element.dependsOnParam) {
          field.hideExpression = () => {
            return this.parameterValues[element.dependsOnParamId]
              ? this.parameterValues[element.dependsOnParamId].toString() !=
                  element.dependsOnParamValue
              : true;
          };
        }
        if (element.repeatable) {
          field.type = "repeat";
          field.templateOptions.addText = "Add " + element.name;
          field.templateOptions.removeText = "Remove " + element.name;
          field.templateOptions.minOccur = element.minOccur;
          field.templateOptions.maxOccur = element.maxOccur;
          field.fieldArray = {
            templateOptions: { label: element.name },
            wrappers: ["panel"],
            fieldGroup: element.elements.map((e) =>
              this.getFieldFromElement(e)
            ),
          };
          if (element.minOccur > 0) {
            field.defaultValue = [];
            for (let i = 0; i < element.minOccur; i++) {
              field.defaultValue.push({});
            }
          }
        } else {
          field.wrappers = ["panel"];
          field.fieldGroup = element.elements.map((e) =>
            this.getFieldFromElement(e)
          );
        }
        break;
    }

    return field;
  }

  getFields(command: DeviceOperation): FormlyFieldConfig[] {
    return command && command.elements
      ? command.elements.map((e) => this.getFieldFromElement(e))
      : [];
  }

  async loadPropertyValues(category: string) {
    let ids = this.filterProperties(category).map((p) => p.name);
    let propertyValues = await this.sshService.loadPropertyValues(
      this.device.id,
      ids
    );
    console.log(propertyValues);
    Object.assign(this.device, propertyValues);
    this.profile.properties.forEach((prop) => {
      if (prop.json) {
        this.tables[prop.name] = {
          headers: this.getHeaders(this.device[prop.name]),
          actions: this.getActions(prop.name),
        };
      }
    });
  }

  async runAction(actionId: string) {
    this.currentAction = this.getAction(actionId);
    if (this.currentAction) {
      if (this.currentAction.confirmation) {
        try {
          let reboot = await this.modalService2.confirm(
            this.currentAction.name,
            "Are you sure to perform this action?",
            Status.DANGER
          );
          console.log(reboot);
          if (reboot) {
            await this.sshService.executeAction(
              this.device.id,
              actionId,
              {},
              "Execute " + this.currentAction.name
            );
          }
        } catch (e) {
          console.log("error is: " + e);
        }
      } else if (this.currentAction.elements.length > 0) {
        this.actionModalRef = this.modalService.show(this.actionModal, {
          backdrop: true,
          ignoreBackdropClick: true,
        });
      } else {
        await this.sshService.executeAction(
          this.device.id,
          actionId,
          this.parameterValues,
          "Execute " + this.currentAction.name
        );
      }
    } else {
      this.alert.danger(
        `Action ${actionId} is not defined in current device profile.`
      );
    }
  }

  getAction(actionId: string): DeviceOperation {
    let result = this.profile.actions.filter((a) => a.id == actionId);
    if (result.length > 0) {
      return result[0];
    } else {
      return null;
    }
  }

  endRunAction() {
    this.sshService.executeAction(
      this.device.id,
      this.currentAction.id,
      this.parameterValues,
      "Execute " + this.currentAction.name
    );
    this.actionModalRef.hide();
  }

  getHeaders(array) {
    if (array && array.length > 0) {
      return Object.keys(array[0]).map((h) => {
        return { name: h, header: h, path: h };
      });
    }
  }

  getActions(propertyName) {
    return this.profile.actions
      .filter((a) => a.tableRowAction && a.tableName == propertyName)
      .map((a) => {
        return {
          type: a.id,
          icon: a.icon,
          callback: async (selectedItem) => {
            console.log("Will run " + a.name + " on item " + selectedItem);
            this.parameterValues = selectedItem;
            await this.runAction(a.id);
          },
        };
      });
  }

  async onNetworkEdit(item) {
    this.parameterValues = item;
    await this.runAction("updateNetwork");
  }

  async onNetworkDelete(item) {
    this.parameterValues = item;
    await this.runAction("deleteNetwork");
  }

  getValue(object, path) {
    return _.get(object, path);
  }

  async upgradePackage(selectedItem) {
    this.parameterValues = selectedItem;
    await this.runAction("upgradePackage");
    console.log(selectedItem);
  }

  async upgradePackages(ids) {
    console.log(ids);
  }
}
