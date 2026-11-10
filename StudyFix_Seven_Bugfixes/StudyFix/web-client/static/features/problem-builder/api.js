import {assignment} from "../../core/api.js";
export const builderApi=(action,data={})=>assignment(`BUILDER_${action}`,data);
