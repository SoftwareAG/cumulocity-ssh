import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Inject,
  OnInit,
  Output,
  TemplateRef,
  ViewChild,
} from "@angular/core";
import {
  FetchClient,
  IManagedObject,
  IUser,
  IdentityService,
  InventoryService,
  QueriesUtil,
  UserService,
} from "@c8y/client";
import {
  ActionControl,
  BuiltInActionType,
  BulkActionControl,
  CellRendererContext,
  Column,
  ColumnDataRecordClassName,
  CustomColumn,
  DATA_GRID_CONFIGURATION_CONTEXT_PROVIDER,
  DATA_GRID_CONFIGURATION_STRATEGY,
  GridConfigContext,
  GridConfigContextProvider,
  Pagination,
  UserPreferencesConfigurationStrategy,
  _,
} from "@c8y/ngx-components";
import { BsModalRef, BsModalService } from "ngx-bootstrap/modal";
import {
  DeviceGridComponent,
  DeviceGridService,
} from "@c8y/ngx-components/device-grid";
import { SSHService } from "../../ssh/service/SSHService";

@Component({
  selector: "ssh-registration",
  templateUrl: "./ssh-registration.component.html",
  providers: [
    {
      provide: DATA_GRID_CONFIGURATION_STRATEGY,
      useClass: UserPreferencesConfigurationStrategy,
    },
    {
      provide: DATA_GRID_CONFIGURATION_CONTEXT_PROVIDER,
      useExisting: SSHRegistrationComponent,
    },
  ],
})
export class SSHRegistrationComponent
  implements OnInit, GridConfigContextProvider, AfterViewInit
{
  devices: IManagedObject[];
  informationText: string;
  deviceToDelete: IManagedObject;
  deviceToReboot: IManagedObject;
  deviceToChange: IManagedObject;
  @ViewChild("deleteDeviceModal", { static: false })
  deleteDeviceModal: TemplateRef<any>;
  deleteDeviceModalRef: BsModalRef;
  @ViewChild("deleteDevicesModal", { static: false })
  deleteDevicesModal: TemplateRef<any>;
  deleteDevicesModalRef: BsModalRef;
  @ViewChild("rebootDeviceModal", { static: false })
  rebootDeviceModal: TemplateRef<any>;
  rebootDeviceModalRef: BsModalRef;
  @ViewChild("rebootDevicesModal", { static: false })
  rebootDevicesModal: TemplateRef<any>;
  rebootDevicesModalRef: BsModalRef;
  @ViewChild("changeDeviceTypeModal", { static: false })
  changeDeviceTypeModal: TemplateRef<any>;
  changeDeviceTypeModalRef: BsModalRef;
  @ViewChild("changeDevicesTypeModal", { static: false })
  changeDevicesTypeModal: TemplateRef<any>;
  changeDevicesTypeModalRef: BsModalRef;
  @ViewChild("deviceGrid")
  deviceGrid: DeviceGridComponent;
  queriesUtil: QueriesUtil;
  columns: Column[];
  pagination: Pagination = {
    pageSize: 30,
    currentPage: 1,
  };
  actionControls: ActionControl[] = [
    {
      type: "CHANGE_TYPE",
      text: "Change Type",
      icon: "pencil",
      callback: (item) => this.changeType(<IManagedObject>item),
    },
  ];
  bulkActionControls: BulkActionControl[] = [
    {
      type: "CHANGE TYPE",
      text: "Change Type",
      icon: "pencil",
      callback: (selectedItemIds) => this.changeTypes(selectedItemIds),
    },
  ];

  @Output() onColumnsChange: EventEmitter<Column[]> = new EventEmitter<
    Column[]
  >();
  @Output() onDeviceQueryStringChange: EventEmitter<string> =
    new EventEmitter<string>();

  provisionDevice: any = {};
  bulkProvisionDevices: any = {};
  devicesToDelete: string[];
  devicesToChange: string[];
  devicesToReboot: string[];
  baseQuery = {
    __filter: {
      __has: "com_sag_ssh_api_Configuration",
    },
    __orderby: [],
  };

  @ViewChild(DeviceGridComponent, { static: true })
  dataGrid: DeviceGridComponent;

  sshAdmin: boolean = false;

  readonly GRID_CONFIG_KEY = "device-grid-all";

  constructor(
    private inventory: InventoryService,
    private fetchClient: FetchClient,
    private modalService: BsModalService,
    private deviceGridService: DeviceGridService,
    public sshService: SSHService,
    public userService: UserService
  ) {
    // _ annotation to mark this string as translatable string.
    this.informationText = _(
      "Ooops! It seems that there is no device to display."
    );
    this.queriesUtil = new QueriesUtil();
  }

  getGridConfigContext(): GridConfigContext {
    return {
      key: this.GRID_CONFIG_KEY,
      defaultColumns: this.deviceGridService.getDefaultColumns(),
      legacyConfigKey: "all-devices-columns-meta_",
      legacyFilterKey: "all-devices-columns-config",
    };
  }

  async ngOnInit(): Promise<void> {
    console.log("In ngOnInit");
    this.sshService.getProfiles();
    this.columns = this.deviceGridService.getDefaultColumns();
    console.log(this.columns);
    const profileColumn = new CustomColumn();
    profileColumn.name = "profile";
    profileColumn.path = "profile";
    profileColumn.header = "Profile";
    profileColumn.cellRendererComponent = ProfileCellRendererComponent;
    this.columns.push(profileColumn);
    this.columns[6].visible = false;
    this.columns[7].visible = false;
    this.columns.push(
      {
        name: "hostname",
        path: "hostname",
        header: "Hostname",
      },
      {
        name: "ip",
        path: "ip",
        header: "IP",
      },
      {
        name: "gateway",
        path: "gateway",
        header: "Gateway",
      },
      {
        name: "netmask",
        path: "netmask",
        header: "Netmask",
      },
      {
        name: "broadcast",
        path: "broadcast",
        header: "Broadcast",
      },
      {
        name: "mac",
        path: "mac",
        header: "Mac address",
      }
    );
    await this.isSshAdmin();
  }

  async ngAfterViewInit(): Promise<void> {
    let user: IUser = (await this.userService.current()).data;
    console.log("in ngAfterViewInit");
    console.log(this.deviceGrid.actionControls);
    console.log(this.sshAdmin);
    if (this.userService.hasRole(user, "ROLE_SSH_ADMIN")) {
      console.log("SSH Admin detected, addtional actions made available!");
      this.deviceGrid.actionControls[1].callback = (item) =>
        this.delete(<IManagedObject>item);
      this.deviceGrid.bulkActionControls.push(
        {
          type: "DELETE",
          text: "Delete devices",
          icon: "minus",
          callback: (itemIds) => this.deleteAll(itemIds),
        },
        {
          type: "REBOOT",
          text: "Reboot devices",
          icon: "refresh",
          callback: (itemIds) => this.rebootAll(itemIds),
        }
      );
    } else {
      this.deviceGrid.actionControls.splice(1, 1);
    }
  }

  async isSshAdmin() {
    let user: IUser = (await this.userService.current()).data;
    console.log(user);
    this.sshAdmin = this.userService.hasRole(user, "ROLE_SSH_ADMIN");
  }

  addDeviceFromForm() {
    this.addDevice(
      this.provisionDevice.deviceName,
      this.provisionDevice.host,
      this.provisionDevice.port,
      this.provisionDevice.user,
      this.provisionDevice.passwordProtected,
      this.provisionDevice.credentials,
      this.provisionDevice.profile
    );
  }

  // Add a managedObject (as device) to the database.
  async addDevice(
    name: string,
    host: string,
    port: number,
    user: string,
    passwordProtected: boolean,
    credentials: string,
    profile: string
  ) {
    let body = JSON.stringify({
      deviceName: name,
      credentials: credentials,
      profile: profile,
      configuration: {
        defaultTimeoutSeconds: 60,
        username: user,
        passwordProtected: passwordProtected,
        host: host,
        port: port,
      },
    });
    console.log(body);
    let response = await this.fetchClient.fetch("/service/ssh/device", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: body,
    });
    console.log(await response.text());
    this.dataGrid.refresh.emit();
  }

  delete(device: IManagedObject) {
    this.deviceToDelete = device;
    this.deleteDeviceModalRef = this.modalService.show(this.deleteDeviceModal, {
      backdrop: true,
      ignoreBackdropClick: true,
    });
  }

  reboot(device: IManagedObject) {
    this.deviceToReboot = device;
    this.rebootDeviceModalRef = this.modalService.show(this.rebootDeviceModal, {
      backdrop: true,
      ignoreBackdropClick: true,
    });
  }

  deleteAll(selectedIds: string[]) {
    this.devicesToDelete = selectedIds;
    this.deleteDevicesModalRef = this.modalService.show(
      this.deleteDevicesModal,
      { backdrop: true, ignoreBackdropClick: true }
    );
  }

  rebootAll(selectedIds: string[]) {
    this.devicesToReboot = selectedIds;
    this.rebootDevicesModalRef = this.modalService.show(
      this.rebootDevicesModal,
      { backdrop: true, ignoreBackdropClick: true }
    );
  }

  async endDelete() {
    await this.sshService.deleteDevice(this.deviceToDelete.id);
    this.deleteDeviceModalRef.hide();
    this.dataGrid.refresh.emit();
  }

  async endDeleteAll() {
    this.devicesToDelete.forEach(async (id) => {
      await this.sshService.deleteDevice(id);
    });
    this.deleteDevicesModalRef.hide();
    this.dataGrid.refresh.emit();
  }

  async endReboot() {
    await this.sshService.executeAction(
      this.deviceToReboot.id,
      "reboot",
      {},
      ""
    );
    this.rebootDeviceModalRef.hide();
    this.dataGrid.refresh.emit();
  }

  async endRebootAll() {
    this.devicesToReboot.forEach(async (id) => {
      await this.sshService.executeAction(id, "reboot", {}, "");
    });
    this.rebootDevicesModalRef.hide();
    this.dataGrid.refresh.emit();
  }

  async changeType(device: IManagedObject) {
    this.deviceToChange = device;
    this.changeDeviceTypeModalRef = this.modalService.show(
      this.changeDeviceTypeModal,
      { backdrop: true, ignoreBackdropClick: true }
    );
  }

  async changeTypes(selectedItemIds: string[]) {
    this.devicesToChange = selectedItemIds;
    this.changeDevicesTypeModalRef = this.modalService.show(
      this.changeDevicesTypeModal,
      { backdrop: true, ignoreBackdropClick: true }
    );
  }

  async endChangeDeviceType(type: string) {
    await this.inventory.update({
      id: this.deviceToChange.id,
      type: type,
      lora_ns_device_LoRaDevice: {},
    });
    this.changeDeviceTypeModalRef.hide();
    this.dataGrid.refresh.emit();
  }

  async endChangeDevicesType(type: string) {
    this.devicesToChange.forEach(async (id) => {
      await this.inventory.update({
        id: id,
        type: type,
        lora_ns_device_LoRaDevice: {},
      });
    });
    this.changeDevicesTypeModalRef.hide();
    this.dataGrid.refresh.emit();
  }
}

@Component({
  template: ` {{ value }} `,
})
export class ProfileCellRendererComponent {
  get value() {
    return this.service.profiles.get(this.context.item.profile).name;
  }

  constructor(
    public context: CellRendererContext,
    @Inject(SSHService) public service: SSHService
  ) {}
}
