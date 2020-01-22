<template>
    <div class="btn-group">
        <button class="btn btn-sm btn-alert btn-hide-dropdown dropdown-toggle" type="button" id="visibility-actions" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false" :data-project="stringNamespace" style="color: black">
            <i class="fas fa-eye"></i> Visibility actions
            <span class="caret"></span>
        </button>
        <ul class="dropdown-menu" aria-labelledby="visibility-actions">
            <li v-for="visibility in visibilities.values">
                <a href="#" class="btn-visibility-change" :data-project="stringNamespace" :data-level="visibility.name" :data-modal="visibility.showModal">
                    {{ visibilityMessage(visibility.name) }} <i v-if="projectVisibility === visibility.name" class="fa fa-check" style="color: black" aria-hidden="true"></i>
                </a>
            </li>
        </ul>
    </div>
</template>

<script>

    import {Visibility} from "../enums";

    export default {
        props: {
            namespace: {
                type: Object,
                required: true
            },
            projectVisibility: {
                type: String,
                required: true
            }
        },
        computed: {
            stringNamespace() {
                return this.namespace.owner + '/' + this.namespace.slug;
            },
            visibilities() {
                return Visibility
            }
        },
        methods: {
            visibilityMessage(visibility) {
                switch (visibility) {
                    case 'public': return 'Public';
                    case 'new': return 'New';
                    case 'needsChanges': return 'Needs changes';
                    case 'needsApproval': return 'Needs approval';
                    case 'softDelete': return 'Soft deleted'
                }
            }
        }
    }
</script>