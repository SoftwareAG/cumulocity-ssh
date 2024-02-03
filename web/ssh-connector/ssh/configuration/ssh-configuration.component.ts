import { CdkDragDrop, moveItemInArray } from "@angular/cdk/drag-drop";
import { Component, OnInit } from "@angular/core";
import { DomSanitizer, SafeUrl } from "@angular/platform-browser";
import { IManagedObject, IUser, UserService } from "@c8y/client";
import { AlertService } from "@c8y/ngx-components";
import { SSHService } from "../service/SSHService";
import { DeviceOperation } from "./DeviceOperation";
import { DeviceOperationElement, ParamType } from "./DeviceOperationElement";
import { Measurement } from "./Measurement";
import { Property } from "./Property";
import { Category } from "./Category";

@Component({
  selector: "ssh-configuration",
  templateUrl: "./ssh-configuration.component.html",
})
export class SSHConfigurationComponent implements OnInit {
  profiles: Map<string, IManagedObject>;
  currentProfile: string;

  operations: Array<DeviceOperation> = new Array<DeviceOperation>();
  measurements: Array<Measurement> = new Array<Measurement>();
  properties: Array<Property> = new Array<Property>();
  categories: Array<Category> = new Array<Category>();
  importedProfiles;

  categoriesTab = "active";
  propertiesTab = "";
  measurementsTab = "";
  actionsTab = "";
  property = {};

  sshAdmin: boolean = false;

  constructor(
    private sshService: SSHService,
    private alertService: AlertService,
    private sanitizer: DomSanitizer,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.loadProfiles();
    this.isSshAdmin();
  }

  async isSshAdmin() {
    let user: IUser = (await this.userService.current()).data;
    console.log(user);
    this.sshAdmin = this.userService.hasRole(user, "ROLE_SSH_ADMIN");
  }

  async loadProfiles() {
    this.profiles = await this.sshService.getProfiles();
    if (this.profiles.size > 0 && !this.currentProfile) {
      this.changeProfile(this.profiles.keys().next().value);
    } else {
      this.changeProfile(this.currentProfile);
    }
  }

  selectedLabelFunction(categories: Category[]) {
    return categories?.map((c) => c.label).join(",");
  }

  selectedCategoriesChanged($event, prop) {
    console.log($event);
    console.log(prop);
    prop.selectedCategories = $event;
    prop.categories = $event.map((c) => c.name);
  }

  changeProfile(profile: string) {
    console.log(profile);
    console.log(this.profiles.get(profile));
    this.currentProfile = profile;
    if (
      this.profiles.get(this.currentProfile) &&
      this.profiles.get(this.currentProfile).categories
    ) {
      this.categories = this.profiles.get(this.currentProfile).categories;
    }
    if (
      this.profiles.get(this.currentProfile) &&
      this.profiles.get(this.currentProfile).properties
    ) {
      this.properties = this.profiles.get(this.currentProfile).properties;
      this.properties.forEach((prop) => {
        if (prop.categories) {
          prop["selectedCategories"] = prop.categories.map((c) => {
            return this.getCategoryByName(c);
          });
        }
      });
    }
    if (
      this.profiles.get(this.currentProfile) &&
      this.profiles.get(this.currentProfile).measurements
    ) {
      this.measurements = this.profiles.get(this.currentProfile).measurements;
    }
    if (
      this.profiles.get(this.currentProfile) &&
      this.profiles.get(this.currentProfile).actions
    ) {
      this.operations = this.profiles.get(this.currentProfile).actions;
      this.operations.forEach((prop) => {
        if (prop.categories) {
          prop["selectedCategories"] = prop.categories.map((c) => {
            return this.getCategoryByName(c);
          });
        }
      });
    }
  }

  getCategoryByName(name: string) {
    let c: Category = null;
    this.categories.forEach((cat) => {
      if (cat.name == name) {
        c = cat;
      }
    });
    return c;
  }

  getJsonProperties() {
    return this.properties.filter((prop) => prop.json);
  }

  async removeProfile() {
    console.log(
      "Will delete profile " + this.profiles.get(this.currentProfile).name
    );
    await this.sshService.deleteProfile(this.currentProfile);
    this.profiles.delete(this.currentProfile);
    if (this.profiles.size > 0) {
      this.changeProfile(this.profiles.keys().next().value);
    }
  }

  onUploadEditorLoad(e) {
    console.log("In onUploadEditorLoad");
    console.log(e);
  }

  goToCategories() {
    this.categoriesTab = "active";
    this.propertiesTab = "";
    this.measurementsTab = "";
    this.actionsTab = "";
  }

  goToProperties() {
    this.categoriesTab = "";
    this.propertiesTab = "active";
    this.measurementsTab = "";
    this.actionsTab = "";
  }

  goToMeasurements() {
    this.categoriesTab = "";
    this.propertiesTab = "";
    this.measurementsTab = "active";
    this.actionsTab = "";
  }

  goToActions() {
    this.categoriesTab = "";
    this.propertiesTab = "";
    this.measurementsTab = "";
    this.actionsTab = "active";
  }

  async createProfile(name: string) {
    console.log("Will create new profile " + name);
    try {
      let profile = await this.sshService.createProfile(name);
      this.profiles.set(profile.id, profile);
      this.changeProfile(profile.id);
    } catch (e) {
      console.error(e);
      this.alertService.danger(`Couldn't create profile ${name}`, e.message);
    }
  }

  async copyProfile(name: string) {
    console.log("Will create new profile " + name);
    try {
      let profile = await this.sshService.createProfile(name);
      this.profiles.set(profile.id, profile);
      this.currentProfile = profile.id;
      await this.saveProfile();
    } catch (e) {
      console.error(e);
    }
  }

  async saveProfile() {
    await this.sshService.saveProfile(
      this.currentProfile,
      this.categories,
      this.properties,
      this.measurements,
      this.operations
    );
    await this.loadProfiles();
  }

  deleteCategory(category) {
    if (category.canDelete) {
      this.categories.splice(this.categories.indexOf(category), 1);
    }
  }

  addCategory() {
    this.categories.push(new Property());
  }

  deleteProperty(property) {
    if (property.canDelete) {
      this.properties.splice(this.properties.indexOf(property), 1);
    }
  }

  addProperty() {
    this.properties.push(new Property());
  }

  deleteMeasurement(measurement) {
    this.measurements.splice(this.measurements.indexOf(measurement), 1);
  }

  addMeasurement() {
    this.measurements.push(new Measurement());
  }

  deleteOperation(operation) {
    this.operations.splice(this.operations.indexOf(operation), 1);
  }

  addOperation() {
    this.operations.push(new DeviceOperation());
  }

  deleteElement(operation: DeviceOperation, element: DeviceOperationElement) {
    operation.elements.splice(operation.elements.indexOf(element), 1);
  }

  moveUp(operation: DeviceOperation, element: DeviceOperationElement) {
    let index = operation.elements.indexOf(element);
    if (index > 0) {
      let el = operation.elements[index];
      operation.elements[index] = operation.elements[index - 1];
      operation.elements[index - 1] = el;
    }
  }

  moveDown(operation: DeviceOperation, element: DeviceOperationElement) {
    let index = operation.elements.indexOf(element);
    if (index !== -1 && index < operation.elements.length - 1) {
      let el = operation.elements[index];
      operation.elements[index] = operation.elements[index + 1];
      operation.elements[index + 1] = el;
    }
  }

  addParam(operation: DeviceOperation) {
    operation.elements.push(new DeviceOperationElement());
  }

  addGroup(operation: DeviceOperation) {
    let element: DeviceOperationElement = new DeviceOperationElement();
    element.type = ParamType.GROUP;
    operation.elements.push(element);
  }

  addValue(param: DeviceOperationElement) {
    if (!param.values) {
      param.values = [];
    }
    param.values.push("");
    console.log(param.values);
  }

  deleteValue(param: DeviceOperationElement, value: string) {
    param.values.splice(param.values.indexOf(value), 1);
    console.log(param.values);
  }

  trackByIdx(index: number, obj: any): any {
    return index;
  }

  isParam(element: DeviceOperationElement): boolean {
    return !element.type || element.type != ParamType.GROUP;
  }

  isGroup(element: DeviceOperationElement): boolean {
    return element.type && element.type == ParamType.GROUP;
  }

  exportProfile(): SafeUrl {
    let profileToExport = this.profiles.get(this.currentProfile);
    let url = window.URL.createObjectURL(
      new Blob([JSON.stringify(profileToExport, null, 4)], {
        type: "text/json",
      })
    );
    return this.sanitizer.bypassSecurityTrustUrl(url);
  }

  async importProfile(e) {
    let profileToImport = await e[0].readAsJson();
    console.log(profileToImport);
    let newProfile = await this.sshService.createProfile(profileToImport.name);
    await this.sshService.saveProfile(
      newProfile.id,
      profileToImport.categories,
      profileToImport.properties,
      profileToImport.measurements,
      profileToImport.actions
    );
    this.loadProfiles();
  }

  drop(event: CdkDragDrop<Property[]>, propertyArray: Property[]) {
    console.log(event);
    moveItemInArray(propertyArray, event.previousIndex, event.currentIndex);
  }

  dropElement(
    event: CdkDragDrop<DeviceOperationElement[]>,
    propertyArray: DeviceOperationElement[]
  ) {
    console.log(event);
    moveItemInArray(propertyArray, event.previousIndex, event.currentIndex);
  }

  stopEvent(e: Event) {
    e.stopImmediatePropagation();
  }
}
